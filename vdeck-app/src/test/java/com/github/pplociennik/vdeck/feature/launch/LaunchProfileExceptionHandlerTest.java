package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
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
 * Verifies that {@link LaunchProfileExceptionHandler} redirects back to the submitted panel with the
 * message and the submitted values as flash attributes, under the names the panels read.
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
     * Verifies that a rejected password update returns to the "Dostęp" panel with the message and the
     * submitted values, the password excluded.
     */
    @Test
    void shouldRedirectToAccessPanelWithFlashedForm_whenServerAccessIsRejected() {
        // GIVEN
        ServerAccessForm form = new ServerAccessForm( "Secret1", false );
        LaunchProfileUpdateException exception =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleUpdateNotStored( exception, post( "/server-settings/access" ),
                redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings/access" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsEntry( "serverAccessForm", new ServerAccessForm( null, false ) )
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
        MockHttpServletRequest request = post( "/server-settings/access" );

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
}
