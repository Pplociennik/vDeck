package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link LaunchProfileExceptionHandler} redirects back to the submitted screen with the
 * message and the submitted values as flash attributes, under the names the screens read.
 */
class LaunchProfileExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LaunchProfileExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh advice before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new LaunchProfileExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rejected server update returns to the "Serwer" screen with the message and the
     * submitted values, the password excluded.
     */
    @Test
    void shouldRedirectToServerScreenWithFlashedForm_whenServerSettingsAreRejected() {
        // GIVEN
        ServerSettingsForm form = serverForm();
        LaunchProfileUpdateException exception =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleUpdateNotStored( exception, post( "/server-settings" ), redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsEntry( "serverSettingsForm", form.withoutPassword() )
                .containsEntry( "launchProfileErrorKey", LaunchProfileMessage.PASSWORD_PART_OF_NAME.getKey() );
        assertThat( redirectAttributes ).isEmpty();
    }

    /**
     * Verifies that a failed game update returns to the "Ustawienia gry" screen with the message and the
     * submitted values.
     */
    @Test
    void shouldRedirectToGameScreenWithFlashedForm_whenGameSettingsCannotBeSaved() {
        // GIVEN
        GameSettingsForm form = new GameSettingsForm( "hard", "", "", "", "", "", null, true, null, null );
        LaunchProfileUpdateException exception = LaunchProfileUpdateException.saveFailed( form,
                new UncheckedIOException( "disk full", new IOException( "disk full" ) ) );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleUpdateNotStored( exception, post( "/game-settings" ), redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/game-settings" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsEntry( "gameSettingsForm", form )
                .containsEntry( "launchProfileErrorKey", LaunchProfileMessage.SAVE_FAILED.getKey() );
    }

    /**
     * Verifies that the advice refuses to run without the exception it answers.
     */
    @Test
    void shouldThrowException_whenExceptionIsNull() {
        // GIVEN
        MockHttpServletRequest request = post( "/server-settings" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.handleUpdateNotStored( null, request, new RedirectAttributesModelMap() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "exception must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static MockHttpServletRequest post( String path ) {
        return new MockHttpServletRequest( "POST", path );
    }

    private static ServerSettingsForm serverForm() {
        return new ServerSettingsForm( "vDeck", 2456, "Dedicated", "Secret1", false, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200 );
    }
}
