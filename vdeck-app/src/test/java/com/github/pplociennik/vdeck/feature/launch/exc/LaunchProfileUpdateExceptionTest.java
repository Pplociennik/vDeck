package com.github.pplociennik.vdeck.feature.launch.exc;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link LaunchProfileUpdateException} keeps the message and the submitted form, never
 * holds the server password, and tells a rejection from a failure by its cause.
 */
class LaunchProfileUpdateExceptionTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rejected access form is kept without its password and the exception has no cause.
     */
    @Test
    void shouldKeepAccessFormWithoutPassword_whenServerAccessIsRejected() {
        // GIVEN
        ServerAccessForm form = accessForm();

        // WHEN
        LaunchProfileUpdateException result =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME );
        assertThat( result.getSubmittedForm() ).isEqualTo( form.withoutSecrets() );
        assertThat( result.isFailure() ).isFalse();
        assertThat( result.getCause() ).isNull();
    }

    /**
     * Verifies that a rejected game form is kept unchanged, because it holds no secret.
     */
    @Test
    void shouldKeepGameForm_whenGameSettingsAreRejected() {
        // GIVEN
        GameSettingsForm form = gameForm();

        // WHEN
        LaunchProfileUpdateException result =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT );
        assertThat( result.getSubmittedForm() ).isSameAs( form );
        assertThat( result.isFailure() ).isFalse();
    }

    /**
     * Verifies that a failed write of the server password keeps its cause and the form without password.
     */
    @Test
    void shouldKeepCauseAndFormWithoutPassword_whenServerAccessCannotBeSaved() {
        // GIVEN
        ServerAccessForm form = accessForm();
        UncheckedIOException cause = writeFailure();

        // WHEN
        LaunchProfileUpdateException result = LaunchProfileUpdateException.saveFailed( form, cause );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
        assertThat( result.getCause() ).isSameAs( cause );
        assertThat( result.isFailure() ).isTrue();
        assertThat( result.getSubmittedForm() ).isEqualTo( form.withoutSecrets() );
    }

    /**
     * Verifies that a failed write of the game settings keeps its cause and the form.
     */
    @Test
    void shouldKeepCauseAndForm_whenGameSettingsCannotBeSaved() {
        // GIVEN
        GameSettingsForm form = gameForm();
        UncheckedIOException cause = writeFailure();

        // WHEN
        LaunchProfileUpdateException result = LaunchProfileUpdateException.saveFailed( form, cause );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
        assertThat( result.getCause() ).isSameAs( cause );
        assertThat( result.isFailure() ).isTrue();
        assertThat( result.getSubmittedForm() ).isSameAs( form );
    }

    /**
     * Verifies that the technical message names the outcome but never a submitted value.
     */
    @Test
    void shouldNotRevealSubmittedValues_whenMessageIsRead() {
        // GIVEN
        LaunchProfileUpdateException exception =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, accessForm() );

        // WHEN
        String result = exception.getMessage();

        // THEN
        assertThat( result ).contains( "INVALID_INPUT" ).doesNotContain( "Secret1" );
    }

    /**
     * Verifies that a rejection without a message is refused.
     */
    @Test
    void shouldThrowException_whenMessageKeyIsNull() {
        // GIVEN
        ServerAccessForm form = accessForm();

        // WHEN
        Throwable thrown = catchThrowable( () -> LaunchProfileUpdateException.rejected( null, form ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "messageKey must not be null" );
    }

    /**
     * Verifies that a rejection without the submitted form is refused.
     */
    @Test
    void shouldThrowException_whenFormIsNull() {
        // GIVEN
        GameSettingsForm form = null;

        // WHEN
        Throwable thrown = catchThrowable( () -> LaunchProfileUpdateException.rejected(
                LaunchProfileMessage.INVALID_INPUT, form ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "form must not be null" );
    }

    /**
     * Verifies that a save failure without its cause is refused, because a failure must keep it.
     */
    @Test
    void shouldThrowException_whenCauseIsNull() {
        // GIVEN
        ServerAccessForm form = accessForm();

        // WHEN
        Throwable thrown = catchThrowable( () -> LaunchProfileUpdateException.saveFailed( form, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "cause must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static ServerAccessForm accessForm() {
        return new ServerAccessForm( "Secret1", false );
    }

    private static GameSettingsForm gameForm() {
        return new GameSettingsForm( "hard", "", "", "", "", "", null, true, null, null );
    }

    private static UncheckedIOException writeFailure() {
        return new UncheckedIOException( "disk full", new IOException( "disk full" ) );
    }
}
