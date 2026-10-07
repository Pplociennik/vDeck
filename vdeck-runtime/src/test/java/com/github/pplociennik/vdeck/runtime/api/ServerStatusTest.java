package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Verifies that each named factory produces a snapshot consistent with its state — in particular that
 * process details appear only where a process actually exists, and that the log file survives a
 * failure so the administrator can still find out what happened.
 */
class ServerStatusTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final long PID = 4242L;

    private static final Instant STARTED_AT = Instant.parse( "2026-09-28T21:43:07Z" );

    private static final Path LOG_FILE = Path.of( "C:\\Valheim\\.vDeck\\log\\run.log" ).toAbsolutePath();

    private ServerStatus underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Selects the snapshot of a panel that manages nothing.
     */
    @BeforeEach
    void setUp() {
        underTest = ServerStatus.stopped();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a stopped panel exposes no process details at all.
     */
    @Test
    void shouldCarryNoProcessDetails_whenStopped() {
        // GIVEN
        // the snapshot of a panel managing nothing

        // WHEN
        ServerState result = underTest.getState();

        // THEN
        assertThat( result ).isEqualTo( ServerState.STOPPED );
        assertThat( underTest.getPid() ).isNull();
        assertThat( underTest.getStartedAt() ).isNull();
        assertThat( underTest.getLogFile() ).isNull();
    }

    /**
     * Verifies that a starting server carries the created process and the log file it writes to.
     */
    @Test
    void shouldCarryProcessDetails_whenStarting() {
        // GIVEN
        // a process that has just been created

        // WHEN
        ServerStatus result = ServerStatus.starting( PID, STARTED_AT, LOG_FILE );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.STARTING );
        assertThat( result.getPid() ).isEqualTo( PID );
        assertThat( result.getStartedAt() ).isEqualTo( STARTED_AT );
        assertThat( result.getLogFile() ).isEqualTo( LOG_FILE );
    }

    /**
     * Verifies that a running server carries the same details as a starting one, differing only in
     * state.
     */
    @Test
    void shouldCarryProcessDetails_whenRunning() {
        // GIVEN
        // a process confirmed to be running

        // WHEN
        ServerStatus result = ServerStatus.running( PID, STARTED_AT, LOG_FILE );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.RUNNING );
        assertThat( result.getPid() ).isEqualTo( PID );
        assertThat( result.getStartedAt() ).isEqualTo( STARTED_AT );
        assertThat( result.getLogFile() ).isEqualTo( LOG_FILE );
    }

    /**
     * Verifies that a failed start keeps the log file but drops the process, because the process is
     * gone while the log is exactly what the administrator needs next.
     */
    @Test
    void shouldKeepOnlyLogFile_whenStartFailed() {
        // GIVEN
        // a launch whose process exited before it became usable

        // WHEN
        ServerStatus result = ServerStatus.startFailed( LOG_FILE );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.START_FAILED );
        assertThat( result.getLogFile() ).isEqualTo( LOG_FILE );
        assertThat( result.getPid() ).isNull();
        assertThat( result.getStartedAt() ).isNull();
    }

    /**
     * Verifies that a crash keeps the log file and drops the process, for the same reason.
     */
    @Test
    void shouldKeepOnlyLogFile_whenCrashed() {
        // GIVEN
        // a server that exited on its own while running

        // WHEN
        ServerStatus result = ServerStatus.crashed( LOG_FILE );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.CRASHED );
        assertThat( result.getLogFile() ).isEqualTo( LOG_FILE );
        assertThat( result.getPid() ).isNull();
    }

    /**
     * Verifies that a failure reported before any log file existed is still representable, so a launch
     * that fails during preparation has a state to report.
     */
    @Test
    void shouldAllowMissingLogFile_whenFailureHappenedBeforeItExisted() {
        // GIVEN
        // a failure that happened before a log file was created

        // WHEN
        ServerStatus result = ServerStatus.startFailed( null );

        // THEN
        assertThat( result.getState() ).isEqualTo( ServerState.START_FAILED );
        assertThat( result.getLogFile() ).isNull();
    }

    /**
     * Verifies that two snapshots describing the same situation are equal, so a caller can compare them
     * to detect that nothing has changed.
     */
    @Test
    void shouldBeEqual_whenDescribingTheSameSituation() {
        // GIVEN
        ServerStatus first = ServerStatus.running( PID, STARTED_AT, LOG_FILE );

        // WHEN
        ServerStatus second = ServerStatus.running( PID, STARTED_AT, LOG_FILE );

        // THEN
        assertThat( first ).isEqualTo( second ).hasSameHashCodeAs( second );
        assertThat( first ).isNotEqualTo( ServerStatus.starting( PID, STARTED_AT, LOG_FILE ) );
    }

    /**
     * Verifies that a starting snapshot cannot be built without the moment the process was created.
     */
    @Test
    void shouldThrowException_whenStartInstantIsNull() {
        // GIVEN
        // no start instant

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerStatus.starting( PID, null, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "startedAt must not be null" );
    }

    /**
     * Verifies that a running snapshot cannot be built without the log file that launch writes to.
     */
    @Test
    void shouldThrowException_whenLogFileOfRunningServerIsNull() {
        // GIVEN
        // no log file

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerStatus.running( PID, STARTED_AT, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "logFile must not be null" );
    }
    /** Failed Stop retains the launch token and every live process detail. */
    @Test
    void shouldRetainLaunchDetails_whenStopFailed() {
        // GIVEN
        ProcessHandle process = mock( ProcessHandle.class );
        given( process.pid() ).willReturn( PID );
        RunningServer server = new RunningServer( process, STARTED_AT, LOG_FILE );
        // WHEN
        underTest = ServerStatus.stopFailed( server, "current-launch", StopFailure.GRACEFUL );
        // THEN
        assertThat( underTest.getState() ).isEqualTo( ServerState.STOP_FAILED );
        assertThat( underTest.getLaunchId() ).isEqualTo( "current-launch" );
        assertThat( underTest.getPid() ).isEqualTo( PID );
        assertThat( underTest.getStartedAt() ).isEqualTo( STARTED_AT );
        assertThat( underTest.getLogFile() ).isEqualTo( LOG_FILE );
        assertThat( underTest.getStopFailure() ).isEqualTo( StopFailure.GRACEFUL );
    }

    /** Starting, running and stopping snapshots preserve the same immutable launch correlation. */
    @Test
    void shouldPreserveCorrelation_whenLiveStateChanges() {
        // GIVEN
        ProcessHandle process = mock( ProcessHandle.class );
        given( process.pid() ).willReturn( PID );
        RunningServer server = new RunningServer( process, STARTED_AT, LOG_FILE );
        // WHEN
        var starting = ServerStatus.starting( server, "current-launch" );
        var running = ServerStatus.running( server, "current-launch" );
        underTest = ServerStatus.stopping( server, "current-launch" );
        // THEN
        assertThat( starting.getState() ).isEqualTo( ServerState.STARTING );
        assertThat( running.getState() ).isEqualTo( ServerState.RUNNING );
        assertThat( underTest.getState() ).isEqualTo( ServerState.STOPPING );
        assertThat( starting.getLaunchId() ).isEqualTo( running.getLaunchId() ).isEqualTo( underTest.getLaunchId() );
    }

    /** An uncertain recovery does not invent process details or an adoptable launch. */
    @Test
    void shouldCarryOptionalAdoption_whenRecoveryIsRequired() {
        // GIVEN
        // Verified and unverified recovery choices.
        // WHEN
        underTest = ServerStatus.recoveryRequired( "saved-launch" );
        var unknown = ServerStatus.recoveryRequired( null );
        // THEN
        assertThat( underTest.getLaunchId() ).isEqualTo( "saved-launch" );
        assertThat( underTest.getPid() ).isNull();
        assertThat( unknown.getState() ).isEqualTo( ServerState.RECOVERY_REQUIRED );
        assertThat( unknown.getLaunchId() ).isNull();
    }

    /** New live factories enforce every non-null component explicitly. */
    @Test
    void shouldRejectNullComponents_whenBuildingCorrelatedStatus() {
        // GIVEN
        RunningServer server = new RunningServer( mock( ProcessHandle.class ), STARTED_AT, LOG_FILE );
        // WHEN
        var noStartServer = catchThrowable( () -> ServerStatus.starting( null, "id" ) );
        var noStartId = catchThrowable( () -> ServerStatus.starting( server, null ) );
        var noRunningServer = catchThrowable( () -> ServerStatus.running( null, "id" ) );
        var noRunningId = catchThrowable( () -> ServerStatus.running( server, null ) );
        var noStoppingServer = catchThrowable( () -> ServerStatus.stopping( null, "id" ) );
        var noStoppingId = catchThrowable( () -> ServerStatus.stopping( server, null ) );
        var noFailedServer = catchThrowable( () -> ServerStatus.stopFailed( null, "id", StopFailure.FORCE ) );
        var noFailedId = catchThrowable( () -> ServerStatus.stopFailed( server, null, StopFailure.FORCE ) );
        var noReason = catchThrowable( () -> ServerStatus.stopFailed( server, "id", null ) );
        // THEN
        assertThat( noStartServer ).isInstanceOf( NullPointerException.class );
        assertThat( noStartId ).isInstanceOf( NullPointerException.class );
        assertThat( noRunningServer ).isInstanceOf( NullPointerException.class );
        assertThat( noRunningId ).isInstanceOf( NullPointerException.class );
        assertThat( noStoppingServer ).isInstanceOf( NullPointerException.class );
        assertThat( noStoppingId ).isInstanceOf( NullPointerException.class );
        assertThat( noFailedServer ).isInstanceOf( NullPointerException.class );
        assertThat( noFailedId ).isInstanceOf( NullPointerException.class );
        assertThat( noReason ).isInstanceOf( NullPointerException.class );
    }


}