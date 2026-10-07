package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies force permission never follows from graceful stop or panel shutdown. */
class StopRequestTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private StopRequest underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a graceful launch-specific request. */
    @BeforeEach
    void setUp() {
        underTest = StopRequest.graceful( "launch" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** Confirms graceful stop carries no implicit force permission. */
    @Test
    void shouldRejectForcePermission_whenGracefulStopIsSelected() {
        // GIVEN
        // A normal Stop request.
        // WHEN
        boolean result = underTest.isForceConfirmed();
        // THEN
        assertThat( result ).isFalse();
        assertThat( underTest.isShutdown() ).isFalse();
    }

    /** Confirms manual permission is scoped to the submitted launch. */
    @Test
    void shouldGrantForcePermission_whenKillIsExplicitlyConfirmed() {
        // GIVEN
        String launch = "selected-launch";
        // WHEN
        underTest = StopRequest.confirmedKill( launch );
        // THEN
        assertThat( underTest.isForceConfirmed() ).isTrue();
        assertThat( underTest.getLaunchId() ).isEqualTo( launch );
        assertThat( underTest.isShutdown() ).isFalse();
    }

    /** Confirms shutdown permits starting processes but never authorizes force. */
    @Test
    void shouldKeepForceForbidden_whenPanelShutdownIsSelected() {
        // GIVEN
        String launch = "launch";
        // WHEN
        underTest = StopRequest.shutdown( launch );
        // THEN
        assertThat( underTest.isForceConfirmed() ).isFalse();
        assertThat( underTest.isShutdown() ).isTrue();
    }

    /**
     * Rejects a missing identifier in each authorization shape.
     *
     * @param variant
     *         selected authorization shape
     */
    @ParameterizedTest( name = "[{index}] null identifier for {0}" )
    @ValueSource( strings = { "graceful", "kill", "shutdown" } )
    void shouldRejectIdentifier_whenItIsNull( String variant ) {
        // GIVEN
        // No selected launch.
        // WHEN
        var failure = catchThrowable( () -> create( variant, null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchId" );
    }

    /**
     * Rejects blank identifiers in every authorization shape.
     *
     * @param variant
     *         selected authorization shape
     */
    @ParameterizedTest( name = "[{index}] blank identifier for {0}" )
    @ValueSource( strings = { "graceful", "kill", "shutdown" } )
    void shouldRejectIdentifier_whenItIsBlank( String variant ) {
        // GIVEN
        String blank = " ";
        // WHEN
        var failure = catchThrowable( () -> create( variant, blank ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /** Identifier input has a hard bound and cannot carry path or redirect syntax. */
    @Test
    void shouldRejectIdentifier_whenOversizedOrUnsafe() {
        // GIVEN
        String oversized = "a".repeat( 101 );
        // WHEN
        var tooLong = catchThrowable( () -> StopRequest.graceful( oversized ) );
        var unsafe = catchThrowable( () -> StopRequest.confirmedKill( "../world" ) );
        // THEN
        assertThat( tooLong ).isInstanceOf( IllegalArgumentException.class );
        assertThat( unsafe ).isInstanceOf( IllegalArgumentException.class );
    }

    private static StopRequest create( String variant, String id ) {
        return switch ( variant ) {
            case "kill" -> StopRequest.confirmedKill( id );
            case "shutdown" -> StopRequest.shutdown( id );
            default -> StopRequest.graceful( id );
        };
    }
}
