package com.github.pplociennik.vdeck.feature.launch.exc;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
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
     * Verifies that a rejected server form is kept without its password and the exception has no cause.
     */
    @Test
    void shouldKeepServerFormWithoutPassword_whenServerSettingsAreRejected() {
        // GIVEN
        ServerSettingsForm form = serverForm();

        // WHEN
        LaunchProfileUpdateException result =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME );
        assertThat( result.getSubmittedForm() ).isEqualTo( form.withoutPassword() );
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
     * Verifies that a failed write of the server settings keeps its cause and the form without password.
     */
    @Test
    void shouldKeepCauseAndFormWithoutPassword_whenServerSettingsCannotBeSaved() {
        // GIVEN
        ServerSettingsForm form = serverForm();
        UncheckedIOException cause = writeFailure();

        // WHEN
        LaunchProfileUpdateException result = LaunchProfileUpdateException.saveFailed( form, cause );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
        assertThat( result.getCause() ).isSameAs( cause );
        assertThat( result.isFailure() ).isTrue();
        assertThat( result.getSubmittedForm() ).isEqualTo( form.withoutPassword() );
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
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, serverForm() );

        // WHEN
        String result = exception.getMessage();

        // THEN
        assertThat( result ).contains( "INVALID_INPUT" ).doesNotContain( "Secret1" ).doesNotContain( "vDeck" );
    }

    /**
     * Verifies that a rejection without a message is refused.
     */
    @Test
    void shouldThrowException_whenMessageKeyIsNull() {
        // GIVEN
        ServerSettingsForm form = serverForm();

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
        ServerSettingsForm form = serverForm();

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

    private static ServerSettingsForm serverForm() {
        return new ServerSettingsForm( "vDeck", 2456, "Dedicated", "Secret1", false, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200 );
    }

    private static GameSettingsForm gameForm() {
        return new GameSettingsForm( "hard", "", "", "", "", "", null, true, null, null );
    }

    private static UncheckedIOException writeFailure() {
        return new UncheckedIOException( "disk full", new IOException( "disk full" ) );
    }
}
