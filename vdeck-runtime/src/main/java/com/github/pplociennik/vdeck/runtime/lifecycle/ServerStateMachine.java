package com.github.pplociennik.vdeck.runtime.lifecycle;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopFailure;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.runtime.api.WorldSaveMissingException;
import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import com.github.pplociennik.vdeck.runtime.spi.WorldSnapshotStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The single place in the application that holds the lifecycle state of the managed Valheim server,
 * the process itself and that launch's log file.
 *
 * <p>Exactly one instance exists per panel, registered as a singleton bean, and it is reached only
 * through the {@link ServerRuntime} facade. Nothing else stores a process identifier, a state or a
 * {@link RunningServer}: a second copy would go stale the moment the server exits on its own.
 *
 * <p>Every state change runs on one private thread — the event queue from {@code design.md} §8 — so an
 * administrator pressing Start twice, and a process exiting while a start is in flight, cannot
 * interleave. {@link #status()} reads a published snapshot without touching that thread, so rendering a
 * page never blocks behind a lifecycle operation.
 *
 * <p>Each launch is tagged with an operation identifier. A notification that arrives after its launch
 * has been superseded is discarded, which is what stops a previous process's exit from overwriting the
 * state of the one that replaced it.
 *
 * <p>Stop requires a complete snapshot before CTRL+C and never escalates to force. Confirmed Kill
 * attempts a snapshot, then forces termination even if copying fails. Deadline, cancellation and
 * identity checks remain mandatory. Failed stop and resource closure preserve the process and token.
 */
@Log4j2
public final class ServerStateMachine implements ServerRuntime, AutoCloseable {

    /**
     * How long a created process has to stay alive before the panel calls it {@code RUNNING}. The panel
     * does not parse the server log yet, so survival is the only health signal available; a process
     * that dies within this window is reported as a failed start rather than a crash.
     */
    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofSeconds( 5 );

    private final GameProcessLauncher launcher;

    private final Duration gracePeriod;

    private final ScheduledExecutorService eventQueue;

    /**
     * Latest snapshot, published for readers on any thread. Written only by the event-queue thread.
     */
    private volatile ServerStatus status = ServerStatus.stopped();

    /**
     * Process currently managed, or {@code null} when none is. Confined to the event-queue thread.
     */
    private RunningServer current;

    /**
     * Identifier of the most recent launch, used to discard notifications belonging to an older one.
     * Confined to the event-queue thread.
     */
    private long operationId;

    private WorldSnapshotStore snapshots;

    private ConsoleControlSender signals;

    private ManagedLaunchStore launchStore;

    private ManagedLaunch launch;

    private ActiveStop activeStop;

    private boolean shuttingDown;

    private boolean invalidRecoveryReported;

    private int pendingWorkers;

    private final AtomicBoolean recoveryRefreshPending = new AtomicBoolean();

    private Supplier< Stream< ProcessHandle > > processListing = ProcessHandle::allProcesses;

    private LongFunction< Optional< ProcessHandle > > processLookup = ProcessHandle::of;

    private ExecutorService workers = new ThreadPoolExecutor( 1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>( 1 ), runnable -> {
                Thread thread = new Thread( runnable, "vdeck-server-stop" );
                thread.setDaemon( true );
                return thread;
            } );

    /**
     * Creates a complete runtime with snapshot, signal and durable recovery boundaries.
     *
     * @param launcher
     *         native process creation
     * @param snapshots
     *         complete BEST_EFFORT snapshot publication
     * @param signals
     *         isolated helper invocation
     * @param launchStore
     *         installation-scoped durable launch token
     * @throws NullPointerException
     *         if any dependency is null
     */
    public ServerStateMachine( @Nonnull GameProcessLauncher launcher, @Nonnull WorldSnapshotStore snapshots,
                               @Nonnull ConsoleControlSender signals, @Nonnull ManagedLaunchStore launchStore ) {
        this( launcher );
        this.snapshots = requireNonNull( snapshots, "snapshots must not be null" );
        this.signals = requireNonNull( signals, "signals must not be null" );
        this.launchStore = requireNonNull( launchStore, "launchStore must not be null" );
        onEventQueue( () -> {
            refreshRecovery();
            return status;
        } );
    }

    /**
     * Creates the state machine with the default grace period.
     *
     * @param launcher
     *         boundary used to create the operating-system process
     * @throws NullPointerException
     *         if {@code launcher} is {@code null}
     */
    public ServerStateMachine( @Nonnull GameProcessLauncher launcher ) {
        this( launcher, DEFAULT_GRACE_PERIOD );
    }

    /**
     * Creates the state machine with an explicit grace period, which lets a test observe the
     * {@code STARTING} to {@code RUNNING} transition without waiting seconds for it.
     *
     * @param launcher
     *         boundary used to create the operating-system process
     * @param gracePeriod
     *         how long a process must stay alive before it counts as running; must not be negative
     * @throws NullPointerException
     *         if {@code launcher} or {@code gracePeriod} is {@code null}
     * @throws IllegalArgumentException
     *         if {@code gracePeriod} is negative
     */
    public ServerStateMachine( @Nonnull GameProcessLauncher launcher, @Nonnull Duration gracePeriod ) {
        this( launcher, gracePeriod, newEventQueue() );
    }

    /**
     * Creates a runtime owning the supplied serial queue, including its shutdown.
     *
     * @param launcher
     *         process creation boundary
     * @param gracePeriod
     *         non-negative survival interval
     * @param eventQueue
     *         serial scheduler controlling lifecycle events
     * @throws NullPointerException
     *         if any dependency is null
     * @throws IllegalArgumentException
     *         if the grace period is negative
     */
    ServerStateMachine( @Nonnull GameProcessLauncher launcher, @Nonnull Duration gracePeriod,
                        @Nonnull ScheduledExecutorService eventQueue ) {
        this.launcher = requireNonNull( launcher, "launcher must not be null" );
        this.gracePeriod = requireNonNull( gracePeriod, "gracePeriod must not be null" );
        if ( gracePeriod.isNegative() ) {
            throw new IllegalArgumentException( "gracePeriod must not be negative but was " + gracePeriod );
        }
        this.eventQueue = requireNonNull( eventQueue, "eventQueue must not be null" );
    }

    /**
     * Creates a complete runtime with controlled I/O boundaries and executors owned by this runtime.
     *
     * @param launcher
     *         process creation boundary
     * @param eventQueue
     *         serialized scheduler
     * @param environment
     *         snapshot, helper, token, worker and process boundaries
     * @throws NullPointerException
     *         if an argument or environment boundary is null
     */
    ServerStateMachine( GameProcessLauncher launcher, ScheduledExecutorService eventQueue, StopEnvironment environment ) {
        this( launcher, DEFAULT_GRACE_PERIOD, eventQueue );
        requireNonNull( environment, "environment must not be null" );
        snapshots = requireNonNull( environment.snapshots(), "snapshots must not be null" );
        signals = requireNonNull( environment.signals(), "signals must not be null" );
        launchStore = requireNonNull( environment.store(), "store must not be null" );
        workers.shutdownNow();
        workers = requireNonNull( environment.workers(), "workers must not be null" );
        processListing = requireNonNull( environment.processes(), "processes must not be null" );
        processLookup = requireNonNull( environment.lookup(), "lookup must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ServerStatus start( @Nonnull LaunchSpec launchSpec ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        if ( launchStore != null ) {
            throw new IllegalStateException( "A managed launch requires its world context" );
        }
        return onEventQueue( () -> doStart( launchSpec, null ) );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ServerStatus start( @Nonnull LaunchSpec launchSpec, @Nonnull WorldContext world ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        requireNonNull( world, "world must not be null" );
        return onEventQueue( () -> doStart( launchSpec, world ) );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull CompletionStage< Void > stop( @Nonnull StopRequest request ) {
        requireNonNull( request, "request must not be null" );
        return onEventQueue( () -> beginStop( request ) );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull CompletionStage< Void > shutdown() {
        return onEventQueue( () -> {
            shuttingDown = true;
            if ( activeStop != null ) {
                return activeStop.completion;
            }
            if ( current == null ) {
                return CompletableFuture.completedFuture( null );
            }
            return beginStop( StopRequest.shutdown( status.getLaunchId() ) );
        } );
    }

    /** {@inheritDoc} */
    @Override
    public void adopt( @Nonnull String launchId ) {
        requireNonNull( launchId, "launchId must not be null" );
        onEventQueue( () -> {
            refreshRecovery();
            if ( shuttingDown || status.getState() != ServerState.RECOVERY_REQUIRED || launch == null
                    || !launch.id().equals( launchId ) ) {
                throw new IllegalStateException( "Saved launch cannot be adopted" );
            }
            ProcessHandle handle = processLookup.apply( launch.identity().pid() ).orElseThrow(
                    () -> new IllegalStateException( "Saved server process no longer exists" ) );
            launch.identity().verify( handle );
            current = new RunningServer( handle, launch.identity().startedAt(), launch.logFile() );
            long generation = ++operationId;
            status = ServerStatus.running( current, launch.id() );
            handle.onExit().thenRun( () -> submit( () -> handleProcessExit( generation ) ) );
            log.info( "Explicitly adopted verified server launch {}", launch.id() );
            return status;
        } );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ServerStatus status() {
        if ( status.getState() == ServerState.RECOVERY_REQUIRED && !eventQueue.isShutdown()
                && recoveryRefreshPending.compareAndSet( false, true ) ) {
            submit( () -> {
                try {
                    refreshRecovery();
                } finally {
                    recoveryRefreshPending.set( false );
                }
            } );
        }
        return status;
    }

    /**
     * Cancels further side effects and releases queues and the installation lock without terminating
     * the server. Normal panel shutdown first waits for {@link #shutdown()}; a surviving process keeps
     * its durable token for explicit adoption by the next panel instance.
     */
    @Override
    public void close() {
        try {
            if ( !eventQueue.isShutdown() ) {
                onEventQueue( () -> {
                    shuttingDown = true;
                    operationId++;
                    if ( activeStop != null ) {
                        failStop( activeStop, StopFailure.DEADLINE,
                                new IllegalStateException( "Runtime resources are closing" ) );
                    }
                    return status;
                } );
            }
        } catch ( RuntimeException exception ) {
            log.warn( "Could not finalize runtime state while closing resources", exception );
        }
        eventQueue.shutdownNow();
        workers.shutdownNow();
        if ( launchStore != null ) {
            launchStore.close();
        }
        log.info( "Server runtime event queue stopped" );
    }

    /**
     * Blocks until every event queued before this call has been processed.
     *
     * <p>This exists so tests can observe a transition that the runtime performs on its own thread —
     * notably the promotion to {@code RUNNING} — without sleeping or polling for it. The event queue is
     * single-threaded and runs due tasks in submission order, so a barrier submitted after an event is
     * guaranteed to run after it. Production code has no reason to call this and must not: waiting for
     * the runtime's thread while rendering a page is exactly what {@link #status()} avoids.
     */
    void awaitIdle() {
        onEventQueue( () -> status );
    }

    /**
     * Creates the process and arms both notifications that can change the state afterwards. Runs on the
     * event-queue thread, so no other lifecycle event can interleave.
     */
    private @Nonnull ServerStatus doStart( @Nonnull LaunchSpec launchSpec, @Nullable WorldContext world ) {
        if ( shuttingDown || pendingWorkers != 0 ) {
            throw new IllegalStateException( "Runtime is closing or old workers have not finished" );
        }
        if ( current == null && launchStore != null ) {
            refreshRecovery();
        }
        ServerState currentState = status.getState();
        if ( !currentState.isStartable() ) {
            throw new IllegalStateException( "Server cannot be started while it is " + currentState );
        }

        long launchOperation = ++operationId;
        log.info( "Starting Valheim server, operation {}, {}", launchOperation, launchSpec );
        RunningServer server = launcher.launch( launchSpec );

        ManagedLaunch captured = null;
        String launchId = UUID.randomUUID().toString();
        if ( world != null ) {
            try {
                ProcessIdentity identity = ProcessIdentity.capture( server.handle(), launchSpec.executable() );
                captured = new ManagedLaunch( launchId, identity, world, server.logFile() );
            } catch ( RuntimeException exception ) {
                // Ownership has not been published; AD-017 permits cleanup of an incomplete start.
                try {
                    server.handle().destroyForcibly();
                } catch ( RuntimeException cleanupFailure ) {
                    exception.addSuppressed( cleanupFailure );
                }
                throw exception;
            }
        }

        current = server;
        launch = captured;
        status = ServerStatus.starting( server, launchId );
        log.info( "Server process {} created, state {}", server.handle().pid(), ServerState.STARTING );

        server.handle().onExit().thenRun( () -> submit( () -> handleProcessExit( launchOperation ) ) );
        eventQueue.schedule( () -> confirmRunning( launchOperation ), gracePeriod.toMillis(),
                TimeUnit.MILLISECONDS );
        if ( launch != null ) {
            // Persistence failure retains published ownership, so Stop and Kill remain available.
            launchStore.write( launch );
        }
        return status;
    }

    /**
     * Promotes a process that survived the grace period to {@code RUNNING}. Does nothing when the
     * launch has been superseded or has already left {@code STARTING}, which is the case when the
     * process exited first.
     */
    private void confirmRunning( long launchOperation ) {
        if ( isSuperseded( launchOperation ) || status.getState() != ServerState.STARTING ) {
            return;
        }
        if ( current == null || !current.handle().isAlive() ) {
            log.debug( "Server process did not survive the grace period, leaving state to the exit handler" );
            return;
        }
        status = ServerStatus.running( current, status.getLaunchId() );
        log.info( "Server process {} stayed alive for {}, state {}", current.handle().pid(), gracePeriod,
                ServerState.RUNNING );
    }

    /**
     * Records that the managed process ended without the panel asking for it. Whether that is a failed
     * start or a crash depends on the state the server had reached.
     */
    private void handleProcessExit( long launchOperation ) {
        if ( isSuperseded( launchOperation ) ) {
            return;
        }
        ServerState previousState = status.getState();
        Path logFile = current == null ? null : current.logFile();
        current = null;
        if ( launchStore != null ) {
            launchStore.clear();
        }
        launch = null;

        switch ( previousState ) {
            case STARTING -> {
                status = ServerStatus.startFailed( logFile );
                log.error( "Server process exited while starting, state {}", ServerState.START_FAILED );
            }
            case RUNNING -> {
                status = ServerStatus.crashed( logFile );
                log.error( "Server process exited on its own, state {}", ServerState.CRASHED );
            }
            case STOPPING, STOP_FAILED -> {
                status = ServerStatus.stopped();
                if ( activeStop != null ) {
                    ActiveStop completed = activeStop;
                    completed.cancelled = true;
                    completed.cancelTimers();
                    activeStop = null;
                    completed.completion.complete( null );
                }
                log.info( "Observed stopped server process, state {}", ServerState.STOPPED );
            }
            default -> log.debug( "Ignoring process exit reported in state {}", previousState );
        }
    }

    private boolean isSuperseded( long launchOperation ) {
        if ( launchOperation == operationId ) {
            return false;
        }
        log.debug( "Ignoring notification of superseded launch {}, current launch is {}", launchOperation,
                operationId );
        return true;
    }

    private CompletionStage< Void > beginStop( StopRequest request ) {
        if ( activeStop != null || pendingWorkers != 0 || current == null || launch == null
                || !launch.id().equals( request.getLaunchId() )
                || ( !status.getState().isStoppable()
                     && !( request.isShutdown() && status.getState() == ServerState.STARTING ) )
                || ( shuttingDown && !request.isShutdown() ) ) {
            throw new IllegalStateException( "Stop request is stale, unavailable, or conflicts with active work" );
        }
        ActiveStop operation = new ActiveStop( request, launch );
        activeStop = operation;
        status = ServerStatus.stopping( current, launch.id() );
        log.info( "Stop accepted for launch {}, manual force permission {}", launch.id(), request.isForceConfirmed() );
        operation.deadlineTask = eventQueue.schedule( () -> {
            if ( isActive( operation ) ) {
                failStop( operation, StopFailure.DEADLINE, new IllegalStateException( "Stop deadline expired" ) );
            }
        }, 5, TimeUnit.MINUTES );
        runWorker( operation, () -> createSnapshot( operation ), StopFailure.SNAPSHOT, () -> {
            if ( operation.request.isForceConfirmed() ) {
                forceStop( operation );
            } else {
                sendSignal( operation, 0 );
            }
        } );
        return operation.completion;
    }

    private void createSnapshot( ActiveStop operation ) {
        try {
            snapshots.create( operation.launch.world(), operation.id, operation::allowed );
        } catch ( RuntimeException exception ) {
            if ( !operation.request.isForceConfirmed() || !operation.allowed()
                    || exception instanceof CancellationException || Thread.currentThread().isInterrupted() ) {
                throw exception;
            }
            log.warn( "Snapshot failed for operation {}; confirmed Kill continues with force termination",
                    operation.id, exception );
        }
    }

    private void sendSignal( ActiveStop operation, int attempt ) {
        if ( !isActive( operation ) ) {
            return;
        }
        runWorker( operation, () -> {
            operation.launch.identity().verify( currentHandle( operation ) );
            if ( !operation.allowed() ) {
                throw new IllegalStateException( "Stop signal is no longer authorized" );
            }
            signals.send( operation.launch.identity(), Duration.ofNanos(
                    Math.min( TimeUnit.SECONDS.toNanos( 10 ), operation.remainingNanos() ) ) );
        }, StopFailure.IDENTITY, () -> {
            log.debug( "Waiting for server exit after CTRL+C attempt {}", attempt + 1 );
            operation.waitTask = eventQueue.schedule( () -> signalWaitExpired( operation, attempt ),
                    attempt == 0 ? 120 : 30, TimeUnit.SECONDS );
        } );
    }

    private void signalWaitExpired( ActiveStop operation, int attempt ) {
        if ( !isActive( operation ) ) {
            return;
        }
        if ( !current.handle().isAlive() ) {
            handleProcessExit( operationId );
            return;
        }
        if ( attempt == 0 ) {
            sendSignal( operation, 1 );
            return;
        }
        failStop( operation, StopFailure.GRACEFUL, new IllegalStateException( "Both CTRL+C attempts exhausted" ) );
    }

    private void forceStop( ActiveStop operation ) {
        if ( !isActive( operation ) ) {
            return;
        }
        runWorker( operation, () -> {
            ProcessHandle handle = currentHandle( operation );
            operation.launch.identity().verify( handle );
            if ( !operation.allowed() ) {
                throw new IllegalStateException( "Force permission expired" );
            }
            if ( !handle.destroyForcibly() && handle.isAlive() ) {
                throw new IllegalStateException( "Operating system refused force termination" );
            }
        }, StopFailure.FORCE, () -> operation.waitTask = eventQueue.schedule( () -> {
            if ( isActive( operation ) ) {
                if ( !current.handle().isAlive() ) {
                    handleProcessExit( operationId );
                } else {
                    failStop( operation, StopFailure.FORCE, new IllegalStateException( "Force exit not observed" ) );
                }
            }
        }, 10, TimeUnit.SECONDS ) );
    }

    private ProcessHandle currentHandle( ActiveStop operation ) {
        // Captured at acceptance; the worker never reads mutable runtime ownership.
        return operation.handle;
    }

    private void runWorker( ActiveStop operation, Runnable work, StopFailure failure, Runnable next ) {
        pendingWorkers++;
        try {
            workers.execute( () -> {
                RuntimeException problem = null;
                try {
                    if ( !operation.allowed() ) {
                        throw new IllegalStateException( "Stop operation expired" );
                    }
                    work.run();
                } catch ( RuntimeException exception ) {
                    problem = exception;
                }
                RuntimeException outcome = problem;
                submit( () -> {
                    pendingWorkers--;
                    if ( !isActive( operation ) ) {
                        return;
                    }
                    if ( !current.handle().isAlive() ) {
                        handleProcessExit( operationId );
                    } else if ( outcome != null ) {
                        failStop( operation, operation.allowed() ? failure : StopFailure.DEADLINE, outcome );
                    } else {
                        next.run();
                    }
                } );
            } );
        } catch ( RuntimeException exception ) {
            pendingWorkers--;
            failStop( operation, failure, exception );
        }
    }

    private boolean isActive( ActiveStop operation ) {
        return activeStop == operation && !operation.cancelled;
    }

    private void failStop( ActiveStop operation, StopFailure failure, RuntimeException exception ) {
        if ( failure == StopFailure.SNAPSHOT && exception instanceof WorldSaveMissingException ) {
            failure = StopFailure.WORLD_SAVE_MISSING;
        }
        operation.cancelled = true;
        operation.cancelTimers();
        activeStop = null;
        if ( current != null ) {
            status = ServerStatus.stopFailed( current, operation.launch.id(), failure );
        }
        if ( failure == StopFailure.WORLD_SAVE_MISSING ) {
            log.warn( "Stop refused for launch {}: required world save is missing; process is retained",
                    operation.launch.id() );
        } else {
            log.error( "Stop failed for launch {}, reason {}; process is retained", operation.launch.id(), failure,
                    exception );
        }
        operation.completion.completeExceptionally( exception );
    }

    private void refreshRecovery() {
        if ( launchStore == null || current != null ) {
            return;
        }
        ManagedLaunch saved;
        RuntimeException tokenFailure = null;
        try {
            saved = launchStore.read().orElse( null );
            invalidRecoveryReported = false;
        } catch ( RuntimeException exception ) {
            saved = null;
            tokenFailure = exception;
        }
        boolean found = false;
        int matches = 0;
        try ( var processes = processListing.get() ) {
            var iterator = processes.iterator();
            while ( iterator.hasNext() ) {
                ProcessHandle handle = iterator.next();
                String command = handle.info().command().orElse( "" );
                if ( !command.isEmpty() && Path.of( command ).toAbsolutePath().normalize().equals( launchStore.executable() )
                        && handle.isAlive() ) {
                    found = true;
                    matches++;
                }
            }
        }
        ProcessHandle savedHandle = saved == null ? null : processLookup.apply( saved.identity().pid() ).orElse( null );
        boolean savedAlive = savedHandle != null && savedHandle.isAlive();
        if ( found || savedAlive ) {
            if ( tokenFailure != null && !invalidRecoveryReported ) {
                log.warn( "Invalid recovery token; an existing server blocks a new launch", tokenFailure );
                invalidRecoveryReported = true;
            }
            launch = null;
            if ( saved != null && matches == 1 ) {
                ProcessHandle handle = processLookup.apply( saved.identity().pid() ).orElse( null );
                if ( handle != null ) {
                    try {
                        saved.identity().verify( handle );
                        launch = saved;
                    } catch ( RuntimeException exception ) {
                        log.warn( "Saved identity cannot be adopted; launch remains blocked" );
                    }
                }
            }
            status = ServerStatus.recoveryRequired( launch == null ? null : launch.id() );
        } else {
            if ( tokenFailure != null ) {
                launchStore.quarantine();
                log.warn( "Quarantined invalid recovery token after confirming no server remains", tokenFailure );
                invalidRecoveryReported = false;
            } else {
                launchStore.clear();
            }
            launch = null;
            if ( status.getState() == ServerState.RECOVERY_REQUIRED ) {
                status = ServerStatus.stopped();
            }
        }
    }

    /**
     * Runs an action on the event-queue thread and waits for it, so the caller of a lifecycle operation
     * gets its outcome. Checked failures of the queue itself become unchecked, while a failure thrown by
     * the action keeps its own type and reaches the caller unchanged.
     */
    private < T > T onEventQueue( Callable< T > action ) {
        try {
            return eventQueue.submit( action ).get();
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException( "Interrupted while waiting for a server lifecycle operation",
                    exception );
        } catch ( ExecutionException exception ) {
            throw asUnchecked( exception.getCause() );
        }
    }

    private static @Nonnull RuntimeException asUnchecked( @Nullable Throwable cause ) {
        if ( cause instanceof RuntimeException runtimeException ) {
            return runtimeException;
        }
        if ( cause instanceof Error error ) {
            throw error;
        }
        return new IllegalStateException( "Server lifecycle operation failed", cause );
    }

    private void submit( @Nonnull Runnable event ) {
        try {
            eventQueue.execute( event );
        } catch ( RuntimeException exception ) {
            // The queue is already shutting down; the panel is stopping and the state no longer matters.
            log.debug( "Dropped a server lifecycle event because the event queue is closed" );
        }
    }

    private static @Nonnull ThreadFactory eventQueueThreadFactory() {
        return runnable -> {
            Thread thread = new Thread( runnable, "vdeck-server-lifecycle" );
            thread.setDaemon( true );
            return thread;
        };
    }

    private static ScheduledExecutorService newEventQueue() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor( 1, eventQueueThreadFactory() );
        executor.setRemoveOnCancelPolicy( true );
        return executor;
    }

    /** One immutable launch/request context with an explicit cancellation guard for worker side effects. */
    private final class ActiveStop {
        private final StopRequest request;
        private final ManagedLaunch launch;
        private final ProcessHandle handle;
        private final String id;
        private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos( 5 );
        private final CompletableFuture< Void > completion = new CompletableFuture<>();
        private volatile boolean cancelled;
        private ScheduledFuture< ? > deadlineTask;
        private ScheduledFuture< ? > waitTask;

        private ActiveStop( StopRequest request, ManagedLaunch launch ) {
            this.request = request;
            this.launch = launch;
            this.handle = current.handle();
            id = launch.id() + "-" + UUID.randomUUID();
        }

        private boolean allowed() {
            return !cancelled && remainingNanos() > 0;
        }

        private long remainingNanos() {
            return Math.max( 0, deadline - System.nanoTime() );
        }

        private void cancelTimers() {
            if ( deadlineTask != null ) {
                deadlineTask.cancel( false );
            }
            if ( waitTask != null ) {
                waitTask.cancel( false );
            }
        }
    }
}
