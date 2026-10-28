package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Verifies that {@link LeavePanelCloseStrategy} leaves the server running without any stop request.
 */
@ExtendWith( MockitoExtension.class )
class LeavePanelCloseStrategyTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerRuntime runtime;

    private LeavePanelCloseStrategy underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the strategy for the mocked runtime.
     */
    @BeforeEach
    void setUp() {
        underTest = new LeavePanelCloseStrategy( runtime );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the strategy is selected by the setting value {@code leave}.
     */
    @Test
    void shouldExposeLeaveMode_whenModeIsRead() {
        // GIVEN
        // the leave strategy

        // WHEN
        PanelCloseMode mode = underTest.getMode();

        // THEN
        assertThat( mode ).isEqualTo( PanelCloseMode.LEAVE );
    }

    /**
     * Verifies that the strategy only reads the state and neither stops nor forces the server.
     */
    @Test
    void shouldNotStopServer_whenApplied() {
        // GIVEN
        given( runtime.status() ).willReturn( ServerStatus.stopped() );

        // WHEN
        underTest.apply();

        // THEN
        then( runtime ).should().status();
        then( runtime ).should( never() ).shutdown();
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
        var failure = catchThrowable( () -> new LeavePanelCloseStrategy( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "runtime" );
    }
}
