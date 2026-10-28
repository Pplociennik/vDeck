package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Verifies that {@link GracefulPanelCloseStrategy} stops the server gracefully and never forces it.
 */
@ExtendWith( MockitoExtension.class )
class GracefulPanelCloseStrategyTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerRuntime runtime;

    private GracefulPanelCloseStrategy underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the strategy for the mocked runtime.
     */
    @BeforeEach
    void setUp() {
        underTest = new GracefulPanelCloseStrategy( runtime );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the strategy is selected by the setting value {@code graceful}.
     */
    @Test
    void shouldExposeGracefulMode_whenModeIsRead() {
        // GIVEN
        // the graceful strategy

        // WHEN
        PanelCloseMode mode = underTest.getMode();

        // THEN
        assertThat( mode ).isEqualTo( PanelCloseMode.GRACEFUL );
    }

    /**
     * Verifies that the strategy requests the runtime's graceful shutdown.
     */
    @Test
    void shouldRequestGracefulShutdown_whenApplied() {
        // GIVEN
        given( runtime.shutdown() ).willReturn( CompletableFuture.completedFuture( null ) );

        // WHEN
        underTest.apply();

        // THEN
        then( runtime ).should().shutdown();
        then( runtime ).should( never() ).stop( any() );
    }

    /**
     * Verifies that a failed graceful shutdown keeps the process without any force and without throwing.
     */
    @Test
    void shouldNotForce_whenGracefulShutdownFails() {
        // GIVEN
        given( runtime.shutdown() ).willReturn(
                CompletableFuture.failedFuture( new IllegalStateException( "Both CTRL+C attempts exhausted" ) ) );
        given( runtime.status() ).willReturn( ServerStatus.stopped() );

        // WHEN
        var failure = catchThrowable( () -> underTest.apply() );

        // THEN
        assertThat( failure ).isNull();
        then( runtime ).should( never() ).stop( any() );
    }

    /**
     * Verifies that a missing runtime is rejected when the strategy is created.
     */
    @Test
    void shouldRejectNull_whenRuntimeIsMissing() {
        // GIVEN
        // no runtime

        // WHEN
        var failure = catchThrowable( () -> new GracefulPanelCloseStrategy( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "runtime" );
    }
}
