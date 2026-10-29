package com.github.pplociennik.vdeck.runtime.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopFailure;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.runtime.api.WorldSaveMissingException;
import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import com.github.pplociennik.vdeck.runtime.spi.WorldSnapshotStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Verifies that the runtime keeps exactly one authoritative view of the managed server: that a start
 * is rejected while one is already running, that a process exit is classified by the state it
 * interrupts, and that a notification belonging to a superseded launch cannot overwrite a newer one.
 *
 * <p>The grace period is zero, so the promotion to {@code RUNNING} is queued immediately and
 * {@code awaitIdle()} is enough to observe it without sleeping or polling.
 */
@ExtendWith( MockitoExtension.class )
class ServerStateMachineTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final long PID = 4242L;

    private static final Instant STARTED_AT = Instant.parse( "2026-09-28T21:43:07Z" );

    private static final Path EXECUTABLE = Path.of( "C:\\Valheim\\valheim_server.exe" ).toAbsolutePath();

    private static final Path LOG_FILE = Path.of( "C:\\Valheim\\.vDeck\\log\\run.log" ).toAbsolutePath();

    @Mock
    private GameProcessLauncher launcher;

    @Mock
    private ProcessHandle processHandle;

    private ScheduledExecutorService controlledQueue;

    @Mock
    private ProcessHandle nextProcess;

    private final List< Runnable > exitEvents = new ArrayList<>();

    private final List< Runnable > confirmations = new ArrayList<>();

    private CompletableFuture< ProcessHandle > exitFuture;

    private ServerStateMachine underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh runtime and the future standing in for the managed process ending.
     */
    @BeforeEach
    void setUp() {
        exitFuture = new CompletableFuture<>();
        underTest = new ServerStateMachine( launcher, Duration.ZERO );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Stops the event queue so no test leaves a thread behind.
     */
    @AfterEach
    void tearDown() {
        underTest.close();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a freshly created runtime manages nothing and exposes no process details.
     */
    @Test
    void shouldReportStopped_whenNothingWasStarted() {
        // GIVEN
        // a runtime that has not been asked to start anything

        // WHEN
        ServerStatus result = underTest.status();

        // THEN
        assertThat( result ).isEqualTo( ServerStatus.stopped() );
        assertThat( result.getPid() ).isNull();
    }

    /**
     * Verifies that a successful launch reports the created process and carries its log file, so the
     * administrator can follow the start. The process is not stubbed as alive, so this snapshot is the
     * one the call itself returns, before any promotion could apply.
     */
    @Test
    void shouldReportStarting_whenProcessWasCreated() {
        // GIVEN
        givenProcessIsCreated();

        // WHEN
        ServerStatus result = underTest.start( launchSpec() );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.STARTING );
        assertThat( result.getPid() ).isEqualTo( PID );
        assertThat( result.getLogFile() ).isEqualTo( LOG_FILE );
    }

    /**
     * Verifies that a process still alive after the grace period is promoted to {@code RUNNING}.
     */
    @Test
    void shouldPromoteToRunning_whenProcessSurvivesGracePeriod() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );

        // WHEN
        underTest.awaitIdle();

        // THEN
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.RUNNING );
        assertThat( underTest.status().getPid() ).isEqualTo( PID );
    }

    /**
     * Verifies that a process exiting before it was confirmed to run ends as a failed start rather than
     * a crash, and that the log file survives so the reason can be read.
     */
    @Test
    void shouldReportStartFailed_whenProcessExitsWhileStarting() {
        // GIVEN
        givenProcessIsCreated();
        underTest.start( launchSpec() );

        // WHEN
        exitFuture.complete( processHandle );
        underTest.awaitIdle();

        // THEN
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.START_FAILED );
        assertThat( underTest.status().getLogFile() ).isEqualTo( LOG_FILE );
        assertThat( underTest.status().getPid() ).isNull();
    }

    /**
     * Verifies that a process exiting after it was confirmed to run is reported as a crash.
     */
    @Test
    void shouldReportCrashed_whenProcessExitsWhileRunning() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // WHEN
        exitFuture.complete( processHandle );
        underTest.awaitIdle();

        // THEN
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.CRASHED );
        assertThat( underTest.status().getLogFile() ).isEqualTo( LOG_FILE );
    }

    /**
     * Verifies that a second start is refused while one server is already running, so two concurrent
     * requests cannot create two processes against the same world.
     */
    @Test
    void shouldRejectStart_whenServerIsAlreadyRunning() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.start( launchSpec() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "RUNNING" );
        verify( launcher ).launch( any() );
    }

    /**
     * Verifies that a failed start does not lock the panel out: the administrator's way out of
     * {@code START_FAILED} is to launch the server again.
     */
    @Test
    void shouldAllowStart_whenPreviousStartFailed() {
        // GIVEN
        givenProcessIsCreated();
        underTest.start( launchSpec() );
        exitFuture.complete( processHandle );
        underTest.awaitIdle();
        givenNextLaunchGetsFreshProcess();

        // WHEN
        ServerStatus result = underTest.start( launchSpec() );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.STARTING );
    }

    /** Verifies that a delayed duplicate exit event cannot overwrite the replacement process details. */
    @Test
    void shouldIgnoreProcessExit_whenItBelongsToSupersededLaunch() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );
        exitFuture.complete( processHandle );
        Runnable oldExit = exitEvents.getFirst();
        oldExit.run();
        givenReplacementProcess();
        underTest.start( launchSpec() );
        confirmations.getLast().run();
        ServerStatus replacement = underTest.status();

        // WHEN
        oldExit.run();

        // THEN
        assertThat( underTest.status() ).isSameAs( replacement );
        assertThat( replacement.getState() ).isEqualTo( ServerState.RUNNING );
        assertThat( replacement.getPid() ).isEqualTo( PID + 1 );
        assertThat( replacement.getLogFile() ).isEqualTo( LOG_FILE.resolveSibling( "next.log" ) );
    }

    /** Verifies that an old grace-period callback cannot promote the replacement prematurely. */
    @Test
    void shouldIgnoreConfirmation_whenItBelongsToSupersededLaunch() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );
        exitFuture.complete( processHandle );
        exitEvents.getFirst().run();
        givenNextLaunchGetsFreshProcess();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        ServerStatus replacement = underTest.status();

        // WHEN
        confirmations.getFirst().run();

        // THEN
        assertThat( underTest.status() ).isSameAs( replacement );
        assertThat( replacement.getState() ).isEqualTo( ServerState.STARTING );
        // Complete the replacement's timer so its live-process stub is exercised independently.
        confirmations.getLast().run();
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.RUNNING );
    }

    /**
     * Verifies that shutting the runtime down ends the managed process, so a panel that stops normally
     * does not leave a server behind for the operating system to reap.
     */
    @Test
    void shouldRetainManagedProcess_whenRuntimeIsClosed() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // WHEN
        underTest.close();

        // THEN
        verify( processHandle, never() ).destroy();
        verify( processHandle, never() ).destroyForcibly();
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.RUNNING );
    }

    /**
     * Verifies that closing a runtime which never started anything is harmless, which is the usual case
     * when the panel is shut down without the server ever having been used.
     */
    @Test
    void shouldDoNothing_whenClosedWithoutManagedProcess() {
        // GIVEN
        // a runtime that has not been asked to start anything

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.close() );

        // THEN
        assertThat( thrown ).isNull();
        assertThat( underTest.status() ).isEqualTo( ServerStatus.stopped() );
    }

    /**
     * Verifies that a launcher failure reaches the caller unchanged and leaves no phantom process
     * behind in the runtime's state.
     */
    @Test
    void shouldPropagateFailureAndStayStopped_whenLauncherFails() {
        // GIVEN
        given( launcher.launch( any() ) )
                .willThrow( new UncheckedIOException( new IOException( "CreateProcessW refused" ) ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.start( launchSpec() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class );
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
    }

    /**
     * Verifies that a null specification is rejected before the launcher is reached.
     */
    @Test
    void shouldThrowException_whenLaunchSpecIsNull() {
        // GIVEN
        // no specification

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.start( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "launchSpec must not be null" );
        verify( launcher, never() ).launch( any() );
    }

    /**
     * Verifies that the runtime cannot be created without the boundary it needs to create processes.
     */
    @Test
    void shouldThrowException_whenLauncherIsNull() {
        // GIVEN
        // no launcher

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerStateMachine( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "launcher must not be null" );
    }

    /**
     * Verifies that a negative grace period is refused, because it would describe a promotion happening
     * before the process was created.
     */
    @Test
    void shouldThrowException_whenGracePeriodIsNegative() {
        // GIVEN
        Duration negative = Duration.ofSeconds( -1 );

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerStateMachine( launcher, negative ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "gracePeriod must not be negative" );
    }

    /** Verifies that a second start cannot create another process while the first is starting. */
    @Test
    void shouldRejectStart_whenServerIsStillStarting() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );

        // WHEN
        var thrown = catchThrowable( () -> underTest.start( launchSpec() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "STARTING" );
        verify( launcher ).launch( any() );
    }

    /**
     * Verifies that overlapping requests create only one process against the same world.
     *
     * @throws Exception
     *         if a coordinated request fails or times out
     */
    @Test
    void shouldCreateOnlyOneProcess_whenStartRequestsOverlap() throws Exception {
        // GIVEN
        givenProcessIsCreated();
        var ready = new CountDownLatch( 2 );
        var release = new CountDownLatch( 1 );
        try ( var callers = Executors.newFixedThreadPool( 2 ) ) {
            Callable< Throwable > request = () -> {
                ready.countDown();
                if ( !release.await( 5, TimeUnit.SECONDS ) ) {
                    throw new IllegalStateException( "Requests were not released" );
                }
                return catchThrowable( () -> underTest.start( launchSpec() ) );
            };
            var first = callers.submit( request );
            var second = callers.submit( request );

            // WHEN
            try {
                assertThat( ready.await( 5, TimeUnit.SECONDS ) ).isTrue();
            } finally {
                release.countDown();
            }
            var results = Arrays.asList( first.get( 5, TimeUnit.SECONDS ), second.get( 5, TimeUnit.SECONDS ) );

            // THEN
            assertThat( results ).filteredOn( result -> result == null ).hasSize( 1 );
            assertThat( results ).filteredOn( result -> result != null ).singleElement()
                    .isInstanceOf( IllegalStateException.class );
            verify( launcher ).launch( any() );
        }
    }

    /** Verifies that a crash permits another launch with a fresh PID, timestamp and log. */
    @Test
    void shouldReplaceProcessDetails_whenStartedAfterCrash() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();
        exitFuture.complete( processHandle );
        underTest.awaitIdle();
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.CRASHED );
        givenReplacementProcess();

        // WHEN
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // THEN
        assertThat( underTest.status().getPid() ).isEqualTo( PID + 1 );
        assertThat( underTest.status().getStartedAt() ).isEqualTo( STARTED_AT.plusSeconds( 10 ) );
        assertThat( underTest.status().getLogFile() ).isEqualTo( LOG_FILE.resolveSibling( "next.log" ) );
    }

    /** Verifies that a launcher exception leaves the runtime usable for a subsequent request. */
    @Test
    void shouldAllowRetry_whenLauncherPreviouslyFailed() {
        // GIVEN
        given( launcher.launch( any() ) ).willThrow( new UncheckedIOException( new IOException( "Synthetic" ) ) );
        assertThat( catchThrowable( () -> underTest.start( launchSpec() ) ) )
                .isInstanceOf( UncheckedIOException.class );
        givenProcessIsCreated();

        // WHEN
        ServerStatus result = underTest.start( launchSpec() );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.STARTING );
    }

    /** Verifies that late notifications cannot falsify an unobserved process exit after resource cleanup. */
    @Test
    void shouldRetainLastState_whenClosedDuringStartAndExitArrivesLater() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );

        // WHEN
        underTest.close();
        exitFuture.complete( processHandle );
        exitEvents.getFirst().run();
        confirmations.getFirst().run();

        // THEN
        assertThat( underTest.status().getState() ).isEqualTo( ServerState.STARTING );
        verify( processHandle, never() ).destroy();
        verify( processHandle, never() ).destroyForcibly();
        verify( controlledQueue ).shutdownNow();
    }

    /** Verifies that closing resources never attempts to terminate the managed process. */
    @Test
    void shouldCloseQueueWithoutTermination_whenProcessIsManaged() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );

        // WHEN
        var thrown = catchThrowable( underTest::close );

        // THEN
        assertThat( thrown ).isNull();
        verify( controlledQueue ).shutdownNow();
    }

    /** Verifies the required grace-period contract independently of its negative-value check. */
    @Test
    void shouldRejectConstruction_whenGracePeriodIsNull() {
        // GIVEN
        // No grace period.

        // WHEN
        var thrown = catchThrowable( () -> new ServerStateMachine( launcher, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "gracePeriod" );
    }

    /** Verifies that the injected queue cannot be missing. */
    @Test
    void shouldRejectConstruction_whenEventQueueIsNull() {
        // GIVEN
        // No queue.

        // WHEN
        var thrown = catchThrowable( () -> new ServerStateMachine( launcher, Duration.ZERO, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "eventQueue" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /** Runs commands immediately but retains callbacks, allowing explicit delayed and duplicate event delivery. */
    private void givenControlledQueue() {
        underTest.close();
        controlledQueue = mock( ScheduledExecutorService.class, invocation -> {
            return switch ( invocation.getMethod().getName() ) {
                case "submit" -> {
                    Callable< ServerStatus > action = invocation.getArgument( 0 );
                    var task = new FutureTask<>( action );
                    task.run();
                    yield task;
                }
                case "execute" -> {
                    exitEvents.add( invocation.getArgument( 0 ) );
                    yield null;
                }
                case "schedule" -> {
                    confirmations.add( invocation.getArgument( 0 ) );
                    yield null;
                }
                default -> RETURNS_DEFAULTS.answer( invocation );
            };
        } );
        underTest = new ServerStateMachine( launcher, Duration.ZERO, controlledQueue );
    }

    private void givenReplacementProcess() {
        given( nextProcess.pid() ).willReturn( PID + 1 );
        given( nextProcess.onExit() ).willReturn( new CompletableFuture<>() );
        given( nextProcess.isAlive() ).willReturn( true );
        var replacement = new RunningServer( nextProcess, STARTED_AT.plusSeconds( 10 ),
                LOG_FILE.resolveSibling( "next.log" ) );
        given( launcher.launch( any() ) ).willReturn( replacement );
    }

    /**
     * Stubs a launcher that creates a process whose exit the test controls. The process is not declared
     * alive, so it stays in {@code STARTING} until the test says otherwise.
     */
    private void givenProcessIsCreated() {
        given( processHandle.pid() ).willReturn( PID );
        given( processHandle.onExit() ).willReturn( exitFuture );
        // Built before the next stubbing starts: creating it reads the handle, and a mock call nested
        // inside an in-progress given(...) would be reported as unfinished stubbing.
        RunningServer created = runningServer();
        willReturn( created ).given( launcher ).launch( any() );
    }

    /**
     * Declares the created process alive, which is what lets the grace period promote it to
     * {@code RUNNING}.
     */
    private void givenProcessStaysAlive() {
        given( processHandle.isAlive() ).willReturn( true );
    }

    /**
     * Points the next launch at a new exit future, so an already completed one cannot end the launch
     * that follows it.
     */
    private void givenNextLaunchGetsFreshProcess() {
        exitFuture = new CompletableFuture<>();
        given( processHandle.onExit() ).willReturn( exitFuture );
        RunningServer created = runningServer();
        willReturn( created ).given( launcher ).launch( any() );
    }

    /**
     * Builds the managed-process description the stubbed launcher returns.
     *
     * @return running server backed by the mocked process handle
     */
    private RunningServer runningServer() {
        return new RunningServer( processHandle, STARTED_AT, LOG_FILE );
    }

    /**
     * Builds a minimal but valid launch specification; its contents do not affect these tests, because
     * the launcher is stubbed.
     *
     * @return launch specification accepted by the runtime
     */
    private static LaunchSpec launchSpec() {
        return new LaunchSpec( EXECUTABLE, List.of( "-nographics" ), Map.of( "SteamAppId", "892970" ),
                EXECUTABLE.getParent(), LOG_FILE );
    }

    /** Deterministic stop cases share the production state machine and replace only external boundaries. */
    @Nested
    class StopOperations {

        // =================================================================================================
        // FIELDS
        // =================================================================================================
        @TempDir
        private Path directory;
        private ManualScheduler queue;
        private ManualWorkers worker;
        private WorldSnapshotStore snapshots;
        private ConsoleControlSender signals;
        private ManagedLaunchStore store;
        private AtomicBoolean alive;
        private ProcessHandle.Info information;
        private LaunchSpec spec;
        private WorldContext world;
        private String id;

        // =================================================================================================
        // BEFORE METHODS
        // =================================================================================================
        /** Starts a verified fixture without real processes, clocks, delays, or native signals. */
        @BeforeEach
        void prepareStop() throws Exception {
            // Production receives canonical paths; a short 8.3 temp path (e.g. RUNNER~1) would look redirected.
            directory = directory.toRealPath();
            underTest.close();
            queue = new ManualScheduler();
            worker = new ManualWorkers();
            snapshots = mock( WorldSnapshotStore.class );
            signals = mock( ConsoleControlSender.class );
            store = mock( ManagedLaunchStore.class );
            alive = new AtomicBoolean( true );
            information = mock( ProcessHandle.Info.class );
            Path executable = Files.writeString( directory.resolve( "valheim_server.exe" ), "fixture" );
            spec = new LaunchSpec( executable, List.of(), Map.of(), directory, directory.resolve( "run.log" ) );
            world = new WorldContext( "Captured", directory.resolve( "save" ), directory.resolve( "backups" ) );
            underTest = new ServerStateMachine( launcher, queue,
                    new StopEnvironment( snapshots, signals, store, worker, Stream::empty,
                            pid -> Optional.of( processHandle ) ) );
            given( processHandle.pid() ).willReturn( PID );
            given( processHandle.onExit() ).willReturn( exitFuture );
            given( processHandle.isAlive() ).willAnswer( invocation -> alive.get() );
            given( processHandle.info() ).willReturn( information );
            given( information.startInstant() ).willReturn( Optional.of( STARTED_AT ) );
            given( information.command() ).willReturn( Optional.of( executable.toString() ) );
            RunningServer server = new RunningServer( processHandle, STARTED_AT, spec.logFile() );
            given( launcher.launch( spec ) ).willReturn( server );
            id = underTest.start( spec, world ).getLaunchId();
            queue.runScheduled( 5 );
            clearInvocations( store );
        }

        // =================================================================================================
        // TESTS
        // =================================================================================================
        /** Success follows observed process exit, never merely helper success. */
        @Test
        void shouldCompleteStop_whenProcessExitIsObserved() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            worker.runNext();
            worker.runNext();
            // WHEN
            alive.set( false );
            exitFuture.complete( processHandle );
            // THEN
            assertThat( result ).isCompleted();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            verify( processHandle, never() ).destroyForcibly();
            verify( store ).clear();
        }

        /**
         * An exit observed while the signal helper has not reported back reports STOPPED at once, but completes
         * the stop only after that worker finishes, so a start requested on completion is admitted.
         */
        @Test
        void shouldCompleteStopAfterWorkerFinishes_whenExitPrecedesWorkerResult() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            worker.runNext();
            alive.set( false );
            exitFuture.complete( processHandle );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            assertThat( result ).isNotDone();
            // WHEN
            worker.runNext();
            // THEN
            assertThat( result ).isCompleted();
            // The launcher fixture returns the same handle, which now stands for the new process.
            alive.set( true );
            assertThat( catchThrowable( () -> underTest.start( spec, world ) ) ).isNull();
            verify( launcher, times( 2 ) ).launch( spec );
        }

        /** Closing the runtime ends the wait for workers of a stop whose exit was already observed. */
        @Test
        void shouldCompleteStop_whenRuntimeClosesBeforeWorkerFinishes() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            worker.runNext();
            alive.set( false );
            exitFuture.complete( processHandle );
            // WHEN
            underTest.close();
            // THEN
            assertThat( result ).isCompleted();
        }

        /** Both failed graceful attempts preserve the process and never authorize Kill. */
        @Test
        void shouldRetainProcess_whenBothGracefulAttemptsExpire() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            // WHEN
            runGracefulLadder();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOP_FAILED );
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.GRACEFUL );
            assertThat( underTest.status().getPid() ).isEqualTo( PID );
            verify( signals, times( 2 ) ).send( any(), any() );
            verify( processHandle, never() ).destroyForcibly();
        }

        /** Snapshot failure stops ordinary Stop before any signal or force. */
        @Test
        void shouldForbidEverySignal_whenSnapshotFails() {
            // GIVEN
            doThrow( new IllegalStateException( "Snapshot failed" ) )
                    .when( snapshots ).create( any(), any(), any() );
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            // WHEN
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.SNAPSHOT );
            verifyNoInteractions( signals );
            verify( processHandle, never() ).destroyForcibly();
        }

        /**
         * Backup I/O, absent first saves and other snapshot failures never revoke confirmed force.
         *
         * @param failureKind
         *         failed backup boundary
         */
        @ParameterizedTest
        @ValueSource( strings = { "missingSave", "storage", "other" } )
        void shouldForceDespiteSnapshotFailure_whenKillIsConfirmed( String failureKind ) {
            // GIVEN
            RuntimeException problem = switch ( failureKind ) {
                case "missingSave" -> new WorldSaveMissingException( new IOException( "missing database" ) );
                case "storage" -> new UncheckedIOException( new IOException( "disk full" ) );
                default -> new IllegalStateException( "snapshot failed" );
            };
            doThrow( problem ).when( snapshots ).create( any(), any(), any() );
            given( processHandle.destroyForcibly() ).willReturn( true );
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            // WHEN
            worker.runNext();
            worker.runNext();
            // THEN
            verify( snapshots ).create( any(), any(), any() );
            verify( processHandle ).destroyForcibly();
            verifyNoInteractions( signals );
            assertThat( result ).isNotDone();
            alive.set( false );
            exitFuture.complete( processHandle );
            assertThat( result ).isCompleted();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
        }

        /** Missing world data is a distinct refusal for ordinary Stop, with the live process retained. */
        @Test
        void shouldRetainProcess_whenGracefulStopHasNoSavedWorld() {
            // GIVEN
            doThrow( new WorldSaveMissingException( new IOException( "missing database" ) ) )
                    .when( snapshots ).create( any(), any(), any() );
            // WHEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.WORLD_SAVE_MISSING );
            verifyNoInteractions( signals );
            verify( processHandle, never() ).destroyForcibly();
        }

        /** Cancellation is control flow, not a failed backup, and cannot authorize late force. */
        @Test
        void shouldRetainProcess_whenKillSnapshotIsCancelled() {
            // GIVEN
            doThrow( new CancellationException( "cancelled" ) ).when( snapshots ).create( any(), any(), any() );
            // WHEN
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            verify( processHandle, never() ).destroyForcibly();
            verifyNoInteractions( signals );
        }

        /** Even after a failed backup, force must verify the original PID, creation time and executable. */
        @Test
        void shouldRefuseForce_whenIdentityChangesAfterSnapshot() {
            // GIVEN
            doThrow( new UncheckedIOException( new IOException( "snapshot failed" ) ) )
                    .when( snapshots ).create( any(), any(), any() );
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            worker.runNext();
            given( information.startInstant() ).willReturn( Optional.of( STARTED_AT.plusSeconds( 1 ) ) );
            // WHEN
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOP_FAILED );
            verify( processHandle, never() ).destroyForcibly();
            verifyNoInteractions( signals );
        }

        /** A delayed force task cannot run after the operation's deadline. */
        @Test
        void shouldRefuseForce_whenDeadlineExpiresAfterSnapshot() {
            // GIVEN
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            worker.runNext();
            // WHEN
            queue.runScheduled( 300 );
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.DEADLINE );
            verify( processHandle, never() ).destroyForcibly();
            verifyNoInteractions( signals );
        }

        /**
         * Native refusal and force exceptions preserve ownership of a still-live process.
         *
         * @param throwsException
         *         whether the process boundary throws instead of returning false
         */
        @ParameterizedTest
        @ValueSource( booleans = { false, true } )
        void shouldRetainProcess_whenForceIsRefused( boolean throwsException ) {
            // GIVEN
            if ( throwsException ) {
                given( processHandle.destroyForcibly() ).willThrow( new IllegalStateException( "native failure" ) );
            } else {
                given( processHandle.destroyForcibly() ).willReturn( false );
            }
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            // WHEN
            worker.runNext();
            worker.runNext();
            // THEN
            assertThat( result ).isCompletedExceptionally();
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.FORCE );
            assertThat( underTest.status().getPid() ).isEqualTo( PID );
            verifyNoInteractions( signals );
        }

        /** If the server exits during copying, Kill completes without attempting to terminate another process. */
        @Test
        void shouldCompleteWithoutForce_whenProcessExitsDuringSnapshot() {
            // GIVEN
            underTest.stop( StopRequest.confirmedKill( id ) );
            alive.set( false );
            // WHEN
            worker.runNext();
            // THEN
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            verify( processHandle, never() ).destroyForcibly();
            verifyNoInteractions( signals );
        }

        /** Confirmed Kill forces after its snapshot attempt without graceful signals or wait timers. */
        @Test
        void shouldForceOnce_whenManualKillIsConfirmed() {
            // GIVEN
            given( processHandle.destroyForcibly() ).willReturn( true );
            var result = underTest.stop( StopRequest.confirmedKill( id ) ).toCompletableFuture();
            worker.runNext();
            verify( snapshots ).create( any(), any(), any() );
            verify( processHandle, never() ).destroyForcibly();
            // WHEN
            worker.runNext();
            // THEN
            verify( processHandle ).destroyForcibly();
            verifyNoInteractions( signals );
            assertThat( result ).isNotDone();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPING );
        }

        /** API success without observed exit remains a failure rather than false STOPPED. */
        @Test
        void shouldKeepProcessManaged_whenForceDoesNotProduceExit() {
            // GIVEN
            given( processHandle.destroyForcibly() ).willReturn( true );
            underTest.stop( StopRequest.confirmedKill( id ) );
            worker.runNext();
            worker.runNext();
            // WHEN
            queue.runScheduled( 10 );
            // THEN
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOP_FAILED );
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.FORCE );
        }

        /** Deadline cancellation prevents a pending worker from sending a late signal. */
        @Test
        void shouldCancelLateWork_whenDeadlineExpiresBeforeSnapshot() {
            // GIVEN
            underTest.stop( StopRequest.confirmedKill( id ) );
            // WHEN
            queue.runScheduled( 300 );
            worker.runNext();
            // THEN
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.DEADLINE );
            verifyNoInteractions( snapshots, signals );
            verify( processHandle, never() ).destroyForcibly();
        }

        /** Process reuse is rejected before sending a helper signal. */
        @Test
        void shouldRejectSignal_whenCreationTimeNoLongerMatches() {
            // GIVEN
            underTest.stop( StopRequest.graceful( id ) );
            worker.runNext();
            given( information.startInstant() ).willReturn( Optional.of( STARTED_AT.plusSeconds( 1 ) ) );
            // WHEN
            worker.runNext();
            // THEN
            assertThat( underTest.status().getStopFailure() ).isEqualTo( StopFailure.IDENTITY );
            verifyNoInteractions( signals );
        }

        /** A form from an earlier launch cannot even start a snapshot. */
        @Test
        void shouldRejectStaleForm_whenLaunchIdentifierDiffers() {
            // GIVEN
            var request = StopRequest.graceful( "old-launch" );
            // WHEN
            var failure = catchThrowable( () -> underTest.stop( request ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( worker.tasks ).isEmpty();
            verifyNoInteractions( snapshots, signals );
        }

        /** Start and competing stop requests cannot interleave with an accepted operation. */
        @Test
        void shouldRejectConflictingCommands_whenStopIsInProgress() {
            // GIVEN
            underTest.stop( StopRequest.graceful( id ) );
            // WHEN
            var kill = catchThrowable( () -> underTest.stop( StopRequest.confirmedKill( id ) ) );
            var start = catchThrowable( () -> underTest.start( spec ) );
            // THEN
            assertThat( kill ).isInstanceOf( IllegalStateException.class );
            assertThat( start ).isInstanceOf( IllegalStateException.class );
        }

        /** Shutdown joins a running Stop without extending its deadline or authorizing force. */
        @Test
        void shouldJoinExistingStop_whenPanelShutsDown() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) );
            // WHEN
            var shutdown = underTest.shutdown();
            runGracefulLadder();
            underTest.close();
            // THEN
            assertThat( shutdown ).isSameAs( result );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOP_FAILED );
            verify( processHandle, never() ).destroy();
            verify( processHandle, never() ).destroyForcibly();
        }

        /** The close strategy's shutdown Kill forces a process left alive by failed graceful shutdown. */
        @Test
        void shouldForceProcess_whenShutdownKillFollowsFailedShutdown() {
            // GIVEN
            given( processHandle.destroyForcibly() ).willReturn( true );
            underTest.shutdown();
            runGracefulLadder();
            underTest.stop( StopRequest.shutdownKill( id ) );
            worker.runNext();
            // WHEN
            worker.runNext();
            // THEN
            verify( processHandle ).destroyForcibly();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPING );
        }

        /** A manual Kill is still refused once the panel shuts down; only the shutdown Kill is admitted. */
        @Test
        void shouldRejectManualKill_whenPanelShutsDown() {
            // GIVEN
            underTest.shutdown();
            runGracefulLadder();
            // WHEN
            var failure = catchThrowable( () -> underTest.stop( StopRequest.confirmedKill( id ) ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOP_FAILED );
            verify( processHandle, never() ).destroyForcibly();
        }

        /** A late exit after failed Stop restores STOPPED without changing the failed completion. */
        @Test
        void shouldObserveLateExit_whenStopPreviouslyFailed() {
            // GIVEN
            var result = underTest.stop( StopRequest.graceful( id ) ).toCompletableFuture();
            runGracefulLadder();
            // WHEN
            alive.set( false );
            exitFuture.complete( processHandle );
            // THEN
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            assertThat( result ).isCompletedExceptionally();
        }

        /** Null arguments are rejected without admitting an operation. */
        @Test
        void shouldRejectNull_whenStopRequestIsMissing() {
            // GIVEN
            // No request.
            // WHEN
            var failure = catchThrowable( () -> underTest.stop( null ) );
            // THEN
            assertThat( failure ).isInstanceOf( NullPointerException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.RUNNING );
        }

        /** An invalidated token revokes a previously offered recovery launch before adoption. */
        @Test
        void shouldRevokeAdoption_whenPreviouslyValidTokenBecomesUnreadable() {
            // GIVEN
            underTest.close();
            var saved = new ManagedLaunch( id, new ProcessIdentity( PID, STARTED_AT, spec.executable() ),
                    world, spec.logFile() );
            given( store.read() ).willReturn( Optional.of( saved ) );
            given( store.executable() ).willReturn( spec.executable() );
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(),
                            () -> Stream.of( processHandle ), pid -> Optional.of( processHandle ) ) );
            catchThrowable( () -> underTest.start( spec, world ) );
            assertThat( underTest.status().getLaunchId() ).isEqualTo( id );
            given( store.read() ).willThrow( new IllegalArgumentException( "Token changed" ) );
            // WHEN
            var failure = catchThrowable( () -> underTest.adopt( id ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.RECOVERY_REQUIRED );
            assertThat( underTest.status().getLaunchId() ).isNull();
            verifyNoInteractions( signals );
            verify( processHandle, never() ).destroyForcibly();
        }

        // =================================================================================================
        // HELPER METHODS
        // =================================================================================================

        /** A live saved PID without readable metadata must never be mistaken for an absent server. */
        @Test
        void shouldBlockStart_whenSavedLiveProcessMetadataIsUnavailable() {
            // GIVEN
            underTest.close();
            ManagedLaunch saved = new ManagedLaunch( id,
                    new ProcessIdentity( PID, STARTED_AT, spec.executable() ),
                    world, spec.logFile() );
            given( store.read() ).willReturn( Optional.of( saved ) );
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(), Stream::empty,
                            pid -> Optional.of( processHandle ) ) );
            // WHEN
            var failure = catchThrowable( () -> underTest.start( spec, world ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.RECOVERY_REQUIRED );
            assertThat( underTest.status().getLaunchId() ).isNull();
            verify( store, never() ).clear();
        }

        /** Recovery is explicit and verifies the complete saved identity before restoring ownership. */
        @Test
        void shouldAdoptSavedLaunch_whenIdentityMatchesAndAdminSelectsIt() {
            // GIVEN
            underTest.close();
            ManagedLaunch saved = new ManagedLaunch( id,
                    new ProcessIdentity( PID, STARTED_AT, spec.executable() ),
                    world, spec.logFile() );
            given( store.read() ).willReturn( Optional.of( saved ) );
            given( store.executable() ).willReturn( spec.executable() );
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(),
                            () -> Stream.of( processHandle ), pid -> Optional.of( processHandle ) ) );
            underTest.status();
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            // WHEN
            underTest.adopt( id );
            // THEN
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.RUNNING );
            assertThat( underTest.status().getLaunchId() ).isEqualTo( id );
            verifyNoInteractions( signals );
            verify( processHandle, never() ).destroyForcibly();
        }

        /** Invalid metadata cannot block a new start after a complete empty process scan. */
        @Test
        void shouldQuarantineInvalidToken_whenNoServerRemains() {
            // GIVEN
            underTest.close();
            given( store.read() ).willThrow( new IllegalArgumentException( "Invalid token" ) );
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(), Stream::empty,
                            pid -> Optional.empty() ) );
            // WHEN
            var result = underTest.start( spec, world );
            // THEN
            assertThat( result.getState() ).isEqualTo( ServerState.STARTING );
            verify( store ).quarantine();
            verify( store, never() ).clear();
        }

        /** A malformed token never grants permission to duplicate a live installation process. */
        @Test
        void shouldBlockStart_whenInvalidTokenHasLiveInstallationProcess() {
            // GIVEN
            underTest.close();
            given( store.read() ).willThrow( new IllegalArgumentException( "Invalid token" ) );
            given( store.executable() ).willReturn( spec.executable() );
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(),
                            () -> Stream.of( processHandle ), pid -> Optional.empty() ) );
            // WHEN
            var failure = catchThrowable( () -> underTest.start( spec, world ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.RECOVERY_REQUIRED );
            verify( store, never() ).quarantine();
            verify( store, never() ).clear();
        }

        /** A failed identity capture cleans up creation before publishing ownership or callbacks. */
        @Test
        void shouldCleanFailedStart_whenProcessIdentityIsUnavailable() {
            // GIVEN
            underTest.close();
            underTest = new ServerStateMachine( launcher, new ManualScheduler(),
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(), Stream::empty,
                            pid -> Optional.empty() ) );
            given( information.startInstant() ).willReturn( Optional.empty() );
            clearInvocations( store, processHandle );
            // WHEN
            var failure = catchThrowable( () -> underTest.start( spec, world ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPED );
            verify( processHandle ).destroyForcibly();
            verify( processHandle, never() ).onExit();
            verify( store, never() ).write( any() );
        }

        /** Metadata persistence failure retains a launch which can still admit a stop. */
        @Test
        void shouldRetainOwnership_whenRecoveryTokenWriteFails() {
            // GIVEN
            underTest.close();
            queue = new ManualScheduler();
            underTest = new ServerStateMachine( launcher, queue,
                    new StopEnvironment( snapshots, signals, store, new ManualWorkers(), Stream::empty,
                            pid -> Optional.empty() ) );
            doThrow( new UncheckedIOException( new IOException( "Storage failed" ) ) ).when( store ).write( any() );
            // WHEN
            var failure = catchThrowable( () -> underTest.start( spec, world ) );
            queue.runScheduled( 5 );
            String launchId = underTest.status().getLaunchId();
            var stopped = underTest.stop( StopRequest.graceful( launchId ) );
            // THEN
            assertThat( failure ).isInstanceOf( UncheckedIOException.class );
            assertThat( underTest.status().getState() ).isEqualTo( ServerState.STOPPING );
            assertThat( stopped ).isNotNull();
            verify( processHandle, never() ).destroyForcibly();
        }

        /** A complete runtime refuses the legacy overload before creating an unmanaged process. */
        @Test
        void shouldRequireWorldContext_whenStartingCompleteRuntime() {
            // GIVEN
            clearInvocations( launcher );
            // WHEN
            var failure = catchThrowable( () -> underTest.start( spec ) );
            // THEN
            assertThat( failure ).isInstanceOf( IllegalStateException.class );
            verifyNoInteractions( launcher );
        }

        private void runGracefulLadder() {
            worker.runNext();
            worker.runNext();
            queue.runScheduled( 120 );
            worker.runNext();
            queue.runScheduled( 30 );
        }


    }

    /** Inline serial event queue with explicit timer delivery; never sleeps or reads a wall clock. */
    private static final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final List< TimedTask > timers = new ArrayList<>();
        private boolean closed;

        /** {@inheritDoc} */
        @Override
        public void execute( Runnable command ) {
            if ( closed ) {
                throw new java.util.concurrent.RejectedExecutionException();
            }
            command.run();
        }
        /** {@inheritDoc} */
        @Override
        public ScheduledFuture< ? > schedule( Runnable command, long delay, TimeUnit unit ) {
            timers.add( new TimedTask( unit.toSeconds( delay ), command ) );
            return mock( ScheduledFuture.class );
        }
        private void runScheduled( long seconds ) {
            TimedTask selected = timers.stream().filter( task -> task.seconds() == seconds ).findFirst().orElseThrow();
            timers.remove( selected );
            selected.command().run();
        }
        /** {@inheritDoc} */
        @Override
        public < V > ScheduledFuture< V > schedule( Callable< V > callable, long delay, TimeUnit unit ) {
            throw new UnsupportedOperationException();
        }
        /** {@inheritDoc} */
        @Override
        public ScheduledFuture< ? > scheduleAtFixedRate( Runnable task, long initial, long period, TimeUnit unit ) {
            throw new UnsupportedOperationException();
        }
        /** {@inheritDoc} */
        @Override
        public ScheduledFuture< ? > scheduleWithFixedDelay( Runnable task, long initial, long delay, TimeUnit unit ) {
            throw new UnsupportedOperationException();
        }
        /** {@inheritDoc} */
        @Override
        public void shutdown() { closed = true; }
        /** {@inheritDoc} */
        @Override
        public List< Runnable > shutdownNow() { closed = true; return List.of(); }
        /** {@inheritDoc} */
        @Override
        public boolean isShutdown() { return closed; }
        /** {@inheritDoc} */
        @Override
        public boolean isTerminated() { return closed; }
        /** {@inheritDoc} */
        @Override
        public boolean awaitTermination( long timeout, TimeUnit unit ) { return closed; }
    }

    /** Manually delivered worker tasks make snapshot/timeout races deterministic. */
    private static final class ManualWorkers extends AbstractExecutorService {
        private final List< Runnable > tasks = new ArrayList<>();
        private boolean closed;
        /** {@inheritDoc} */
        @Override
        public void execute( Runnable command ) { tasks.add( command ); }
        private void runNext() { tasks.removeFirst().run(); }
        /** {@inheritDoc} */
        @Override
        public void shutdown() { closed = true; }
        /** {@inheritDoc} */
        @Override
        public List< Runnable > shutdownNow() { closed = true; return List.of(); }
        /** {@inheritDoc} */
        @Override
        public boolean isShutdown() { return closed; }
        /** {@inheritDoc} */
        @Override
        public boolean isTerminated() { return closed; }
        /** {@inheritDoc} */
        @Override
        public boolean awaitTermination( long timeout, TimeUnit unit ) { return closed; }
    }

    /** A scheduled callback and its delay, used only by the deterministic scheduler. */
    private record TimedTask( long seconds, Runnable command ) {
    }

}
