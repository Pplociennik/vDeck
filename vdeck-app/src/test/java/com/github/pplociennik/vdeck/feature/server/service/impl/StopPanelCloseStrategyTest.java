package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopFailure;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Verifies that {@link StopPanelCloseStrategy} forces the server only after graceful shutdown left it alive.
 */
@ExtendWith( MockitoExtension.class )
class StopPanelCloseStrategyTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String LAUNCH_ID = "launch-1";

    @Mock
    private ServerRuntime runtime;

    private StopPanelCloseStrategy underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the strategy for the mocked runtime.
     */
    @BeforeEach
    void setUp() {
        underTest = new StopPanelCloseStrategy( runtime );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the strategy is selected by the default setting value {@code stop}.
     */
    @Test
    void shouldExposeStopMode_whenModeIsRead() {
        // GIVEN
        // the stop strategy

        // WHEN
        PanelCloseMode mode = underTest.getMode();

        // THEN
        assertThat( mode ).isEqualTo( PanelCloseMode.STOP );
    }

    /**
     * Verifies that a successful graceful shutdown ends the strategy without any force.
     */
    @Test
    void shouldNotForce_whenGracefulShutdownSucceeds() {
        // GIVEN
        given( runtime.shutdown() ).willReturn( CompletableFuture.completedFuture( null ) );

        // WHEN
        underTest.apply();

        // THEN
        then( runtime ).should().shutdown();
        then( runtime ).should( never() ).stop( any() );
    }

    /**
     * Verifies that a process left alive in {@code STOP_FAILED} receives a shutdown Kill for its own launch.
     */
    @Test
    void shouldSendShutdownKill_whenGracefulShutdownLeavesProcessAlive() {
        // GIVEN
        givenFailedShutdownLeaving( ServerState.STOP_FAILED );
        given( runtime.stop( any() ) ).willReturn( CompletableFuture.completedFuture( null ) );

        // WHEN
        underTest.apply();

        // THEN
        ArgumentCaptor< StopRequest > request = ArgumentCaptor.forClass( StopRequest.class );
        then( runtime ).should().stop( request.capture() );
        assertThat( request.getValue().getLaunchId() ).isEqualTo( LAUNCH_ID );
        assertThat( request.getValue().isForceConfirmed() ).isTrue();
        assertThat( request.getValue().isShutdown() ).isTrue();
    }

    /**
     * Verifies that a failed shutdown Kill is logged and never thrown, so panel shutdown goes on.
     */
    @Test
    void shouldNotThrow_whenShutdownKillFails() {
        // GIVEN
        givenFailedShutdownLeaving( ServerState.STOP_FAILED );
        given( runtime.stop( any() ) ).willReturn(
                CompletableFuture.failedFuture( new IllegalStateException( "Force exit not observed" ) ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.apply() );

        // THEN
        assertThat( failure ).isNull();
        then( runtime ).should().stop( any() );
    }

    /**
     * Verifies that no force is attempted when the failed shutdown did not leave a stop-failed process, for
     * example after the process exited or the wait ended while the stop was still running.
     *
     * @param state
     *         state reported after the failed graceful shutdown
     */
    @ParameterizedTest( name = "[{index}] state={0} is not forced" )
    @EnumSource( value = ServerState.class, names = { "STOPPED", "STOPPING", "RECOVERY_REQUIRED" } )
    void shouldNotForce_whenFailedShutdownLeavesNoStopFailedProcess( ServerState state ) {
        // GIVEN
        givenFailedShutdownLeaving( state );

        // WHEN
        underTest.apply();

        // THEN
        then( runtime ).should( never() ).stop( any() );
    }

    /**
     * Verifies that a shutdown the runtime refuses to admit is treated as a failed graceful shutdown.
     */
    @Test
    void shouldForce_whenRuntimeRefusesGracefulShutdown() {
        // GIVEN
        given( runtime.shutdown() ).willThrow( new IllegalStateException( "Unavailable" ) );
        givenStatus( ServerState.STOP_FAILED );
        given( runtime.stop( any() ) ).willReturn( CompletableFuture.completedFuture( null ) );

        // WHEN
        assertThatCode( () -> underTest.apply() ).doesNotThrowAnyException();

        // THEN
        then( runtime ).should().stop( any() );
    }

    /**
     * Verifies that a missing runtime is rejected when the strategy is created.
     */
    @Test
    void shouldRejectNull_whenRuntimeIsMissing() {
        // GIVEN
        // no runtime

        // WHEN
        var failure = catchThrowable( () -> new StopPanelCloseStrategy( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "runtime" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenFailedShutdownLeaving( ServerState state ) {
        given( runtime.shutdown() ).willReturn(
                CompletableFuture.failedFuture( new IllegalStateException( "Both CTRL+C attempts exhausted" ) ) );
        givenStatus( state );
    }

    private void givenStatus( ServerState state ) {
        Path logFile = Path.of( "run.log" ).toAbsolutePath();
        RunningServer server = new RunningServer( mock( ProcessHandle.class ), Instant.EPOCH, logFile );
        ServerStatus status = switch ( state ) {
            case STOP_FAILED -> ServerStatus.stopFailed( server, LAUNCH_ID, StopFailure.GRACEFUL );
            case STOPPING -> ServerStatus.stopping( server, LAUNCH_ID );
            case RECOVERY_REQUIRED -> ServerStatus.recoveryRequired( LAUNCH_ID );
            default -> ServerStatus.stopped();
        };
        given( runtime.status() ).willReturn( status );
    }
}
