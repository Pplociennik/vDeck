package com.github.pplociennik.vdeck.feature.launch.domain;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the factories of {@link LaunchProfileUpdateResult} and the success check derived from the
 * presence of a message key.
 */
class LaunchProfileUpdateResultTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LaunchProfileUpdateResult underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a successful result carries no message.
     */
    @Test
    void shouldBeSuccessfulWithoutMessage_whenCreatedAsSuccess() {
        // GIVEN
        // nothing to explain

        // WHEN
        underTest = LaunchProfileUpdateResult.success();

        // THEN
        assertThat( underTest.isSuccessful() ).isTrue();
        assertThat( underTest.messageKey() ).isNull();
    }

    /**
     * Verifies that a rejected result is not successful and carries its message.
     */
    @Test
    void shouldCarryMessage_whenCreatedAsRejection() {
        // GIVEN
        LaunchProfileMessage message = LaunchProfileMessage.INVALID_INPUT;

        // WHEN
        underTest = LaunchProfileUpdateResult.rejected( message );

        // THEN
        assertThat( underTest.isSuccessful() ).isFalse();
        assertThat( underTest.messageKey() ).isEqualTo( message );
    }

    /**
     * Verifies that a rejection without a message is refused, because the screen would have nothing to
     * show.
     */
    @Test
    void shouldThrowException_whenRejectionMessageIsNull() {
        // GIVEN
        // no message at all

        // WHEN
        Throwable thrown = catchThrowable( () -> LaunchProfileUpdateResult.rejected( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "messageKey must not be null" );
    }
}