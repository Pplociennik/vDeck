package com.github.pplociennik.vdeck.feature.server.exc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the two process outcomes and non-null contracts of their named factories. */
class ServerStartExceptionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ServerStartException underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a fresh rule rejection for each test. */
    @BeforeEach
    void setUp() {
        underTest = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that a rule rejection retains its key and has no technical failure cause. */
    @Test
    void shouldRetainReasonWithoutCause_whenStartIsRejected() {
        // GIVEN
        // A rejection created in setUp.

        // WHEN
        var key = underTest.getMessageKey();
        boolean failure = underTest.isFailure();

        // THEN
        assertThat( key ).isEqualTo( ServerMessage.ALREADY_RUNNING );
        assertThat( failure ).isFalse();
        assertThat( underTest ).hasNoCause();
    }

    /** Verifies that technical failures use the start-failure key and retain the original cause. */
    @Test
    void shouldRetainCause_whenStartFailsTechnically() {
        // GIVEN
        var cause = new IllegalStateException( "Synthetic process creation failure" );

        // WHEN
        underTest = ServerStartException.startFailed( cause );

        // THEN
        assertThat( underTest.isFailure() ).isTrue();
        assertThat( underTest.getMessageKey() ).isEqualTo( ServerMessage.START_FAILED );
        assertThat( underTest ).hasCause( cause );
    }

    /** Verifies that a rejection cannot omit its user-facing message key. */
    @Test
    void shouldThrowException_whenMessageKeyIsNull() {
        // GIVEN
        // No message key.

        // WHEN
        var thrown = catchThrowable( () -> ServerStartException.rejected( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "messageKey" );
    }

    /** Verifies that a technical failure cannot be created without a cause. */
    @Test
    void shouldThrowException_whenCauseIsNull() {
        // GIVEN
        // No cause.

        // WHEN
        var thrown = catchThrowable( () -> ServerStartException.startFailed( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "cause" );
    }
}
