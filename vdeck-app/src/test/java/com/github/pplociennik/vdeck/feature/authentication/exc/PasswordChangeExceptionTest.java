package com.github.pplociennik.vdeck.feature.authentication.exc;

import com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PasswordChangeException} keeps its message and requires one.
 */
class PasswordChangeExceptionTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the exception keeps the message and names the failed check in its technical message.
     */
    @Test
    void shouldKeepMessageKey_whenCreated() {
        // GIVEN
        PasswordChangeMessage messageKey = PasswordChangeMessage.NEW_PASSWORD_MISMATCH;

        // WHEN
        PasswordChangeException result = new PasswordChangeException( messageKey );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( messageKey );
        assertThat( result.getMessage() ).contains( "NEW_PASSWORD_MISMATCH" );
        assertThat( result.getCause() ).isNull();
    }

    /**
     * Verifies that a rejection without a message is refused.
     */
    @Test
    void shouldThrowException_whenMessageKeyIsNull() {
        // GIVEN
        // no message at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new PasswordChangeException( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "messageKey must not be null" );
    }
}
