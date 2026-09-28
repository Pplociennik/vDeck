package com.github.pplociennik.vdeck.runtime.lifecycle;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    /**
     * Verifies that the exit of a replaced launch cannot overwrite the state of the launch that
     * replaced it, which is what the operation identifier exists for.
     */
    @Test
    void shouldIgnoreProcessExit_whenItBelongsToSupersededLaunch() {
        // GIVEN
        givenProcessIsCreated();
        underTest.start( launchSpec() );
        CompletableFuture< ProcessHandle > supersededExit = exitFuture;
        supersededExit.complete( processHandle );
        underTest.awaitIdle();

        givenNextLaunchGetsFreshProcess();
        givenProcessStaysAlive();
        underTest.start( launchSpec() );
        underTest.awaitIdle();

        // WHEN
        supersededExit.complete( processHandle );
        underTest.awaitIdle();

        // THEN
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

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

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
        given( launcher.launch( any() ) ).willReturn( created );
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
        given( launcher.launch( any() ) ).willReturn( created );
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