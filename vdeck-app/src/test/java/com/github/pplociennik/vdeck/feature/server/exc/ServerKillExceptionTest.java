package com.github.pplociennik.vdeck.feature.server.exc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies safe process keys, technical causes and the required key boundary. */
class ServerKillExceptionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private ServerKillException underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a rejection without a technical cause. */
    @BeforeEach
    void setUp() {
        underTest = new ServerKillException( ServerMessage.KILL_REJECTED, null );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** Rejections expose the safe closed key and no invented failure cause. */
    @Test
    void shouldRetainSafeKey_whenRequestIsRejected() {
        // GIVEN
        // A rejection.
        // WHEN
        var result = underTest.getMessageKey();
        // THEN
        assertThat( result ).isEqualTo( ServerMessage.KILL_REJECTED );
        assertThat( underTest ).hasNoCause();
    }

    /** Technical causes remain available to the advice's single logging boundary. */
    @Test
    void shouldRetainCause_whenAdmissionFails() {
        // GIVEN
        var cause = new IllegalStateException( "Technical fixture" );
        // WHEN
        underTest = new ServerKillException( ServerMessage.STOP_FAILED, cause );
        // THEN
        assertThat( underTest ).hasCause( cause );
        assertThat( underTest.getMessageKey() ).isEqualTo( ServerMessage.STOP_FAILED );
    }

    /** Null keys cannot escape to rendering as an unknown process result. */
    @Test
    void shouldRejectKey_whenNull() {
        // GIVEN
        // No key.
        // WHEN
        var failure = catchThrowable( () -> new ServerKillException( null, null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "messageKey" );
    }
}
