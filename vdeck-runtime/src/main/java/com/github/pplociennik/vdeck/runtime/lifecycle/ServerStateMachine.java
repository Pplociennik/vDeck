package com.github.pplociennik.vdeck.runtime.lifecycle;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

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
 * <p>This increment implements the start half of the lifecycle. {@code STOPPING}, {@code STOP_FAILED}
 * and process recovery arrive with the features that produce them.
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
        this( launcher, gracePeriod, Executors.newSingleThreadScheduledExecutor( eventQueueThreadFactory() ) );
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

    @Override
    public @Nonnull ServerStatus start( @Nonnull LaunchSpec launchSpec ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        return onEventQueue( () -> doStart( launchSpec ) );
    }

    @Override
    public @Nonnull ServerStatus status() {
        return status;
    }

    /**
     * Stops the managed server and then the event queue. Called when the panel shuts down normally.
     *
     * <p>Termination is currently forcible, because the graceful CTRL+C shutdown Valheim prefers
     * belongs to the stop feature and does not exist yet. This call is therefore an ordered, logged
     * version of what the job object would do anyway a moment later; it is replaced by the stop ladder
     * — save the world, then CTRL+C, then force — once that feature lands.
     *
     * <p>A panel that is killed or crashes never reaches this method. That path is covered by the job
     * object the launcher puts the process in, which the operating system tears down on its own.
     */
    @Override
    public void close() {
        try {
            onEventQueue( this::terminateManagedProcess );
        } catch ( RuntimeException exception ) {
            log.warn( "Could not stop the managed server while shutting down", exception );
        }
        eventQueue.shutdownNow();
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
     * Ends the managed process, if there is one. Runs on the event-queue thread, so it cannot race with
     * a start or with a process-exit notification.
     */
    private @Nonnull ServerStatus terminateManagedProcess() {
        if ( current == null ) {
            return status;
        }
        long pid = current.handle().pid();
        log.info( "Stopping managed server process {} because the panel is shutting down", pid );
        current.handle().destroy();
        current = null;
        status = ServerStatus.stopped();
        return status;
    }

    /**
     * Creates the process and arms both notifications that can change the state afterwards. Runs on the
     * event-queue thread, so no other lifecycle event can interleave.
     */
    private @Nonnull ServerStatus doStart( @Nonnull LaunchSpec launchSpec ) {
        ServerState currentState = status.getState();
        if ( !currentState.isStartable() ) {
            throw new IllegalStateException( "Server cannot be started while it is " + currentState );
        }

        long launchOperation = ++operationId;
        log.info( "Starting Valheim server, operation {}, {}", launchOperation, launchSpec );
        RunningServer server = launcher.launch( launchSpec );

        current = server;
        status = ServerStatus.starting( server.handle().pid(), server.startedAt(), server.logFile() );
        log.info( "Server process {} created, state {}", server.handle().pid(), ServerState.STARTING );

        server.handle().onExit().thenRun( () -> submit( () -> handleProcessExit( launchOperation ) ) );
        eventQueue.schedule( () -> confirmRunning( launchOperation ), gracePeriod.toMillis(),
                TimeUnit.MILLISECONDS );
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
        status = ServerStatus.running( current.handle().pid(), current.startedAt(), current.logFile() );
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

        switch ( previousState ) {
            case STARTING -> {
                status = ServerStatus.startFailed( logFile );
                log.error( "Server process exited while starting, state {}", ServerState.START_FAILED );
            }
            case RUNNING -> {
                status = ServerStatus.crashed( logFile );
                log.error( "Server process exited on its own, state {}", ServerState.CRASHED );
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

    /**
     * Runs an action on the event-queue thread and waits for it, so the caller of a lifecycle operation
     * gets its outcome. Checked failures of the queue itself become unchecked, while a failure thrown by
     * the action keeps its own type and reaches the caller unchanged.
     */
    private @Nonnull ServerStatus onEventQueue( @Nonnull Callable< ServerStatus > action ) {
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
}
