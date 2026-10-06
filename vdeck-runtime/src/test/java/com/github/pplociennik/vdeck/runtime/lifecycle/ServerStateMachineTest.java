package com.github.pplociennik.vdeck.runtime.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    void shouldStopManagedProcess_whenRuntimeIsClosed() {
        // GIVEN
        givenProcessIsCreated();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // WHEN
        underTest.close();

        // THEN
        verify( processHandle ).destroy();
        assertThat( underTest.status() ).isEqualTo( ServerStatus.stopped() );
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

    /** Verifies that late exit delivery after shutdown cannot turn a stopped runtime into a failed start. */
    @Test
    void shouldRemainStopped_whenClosedDuringStartAndExitArrivesLater() {
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
        assertThat( underTest.status() ).isEqualTo( ServerStatus.stopped() );
        verify( processHandle ).destroy();
        verify( controlledQueue ).shutdownNow();
    }

    /** Verifies that a termination failure still shuts down the lifecycle executor. */
    @Test
    void shouldCloseQueue_whenProcessTerminationThrows() {
        // GIVEN
        givenControlledQueue();
        givenProcessIsCreated();
        underTest.start( launchSpec() );
        given( processHandle.destroy() ).willThrow( new IllegalStateException( "Synthetic termination failure" ) );

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
}
