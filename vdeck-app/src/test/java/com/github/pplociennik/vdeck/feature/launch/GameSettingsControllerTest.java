package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.service.GameSettingsDisplayService;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies MVC behavior of {@link GameSettingsController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class GameSettingsControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String VIEW = "launch/game-settings";

    @Mock
    private LaunchProfileService launchProfileService;

    @Mock
    private GameSettingsDisplayService displayService;

    private GameSettingsController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with isolated services.
     */
    @BeforeEach
    void setUp() {
        underTest = new GameSettingsController( launchProfileService, displayService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that opening the screen fills the form from the stored profile and publishes the option
     * lists and the remembered visibility of the advanced section.
     */
    @Test
    void shouldFillFormFromStoredProfile_whenPageIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        given( displayService.isAdvancedShown() ).willReturn( true );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showGameSettings( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "gameSettingsForm" ) ).isEqualTo( GameSettingsForm.from( storedProfile() ) );
        assertThat( model.get( "advancedShown" ) ).isEqualTo( true );
        assertThat( model ).containsKeys( "worldPresets", "worldModifiers", "worldKeys" );
    }

    /**
     * Verifies that annotation failures are reported without calling the service.
     */
    @Test
    void shouldReturnValidationErrorKey_whenBindingHasErrors() {
        // GIVEN
        GameSettingsForm form = submittedForm();
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "gameSettingsForm" );
        bindingResult.addError( new FieldError( "gameSettingsForm", "preset", "too long" ) );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.updateGameSettings( form, bindingResult, model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "gameSettingsErrorKey" ) ).isEqualTo( LaunchProfileMessage.INVALID_INPUT.getKey() );
        verify( launchProfileService, never() ).updateGameSettings( any() );
    }

    /**
     * Verifies that stored settings redirect back to the screen with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenSettingsAreStored() {
        // GIVEN
        GameSettingsForm form = submittedForm();
        given( launchProfileService.updateGameSettings( form ) ).willReturn( LaunchProfileUpdateResult.success() );

        // WHEN
        String result = underTest.updateGameSettings( form, new BeanPropertyBindingResult( form, "form" ),
                new ExtendedModelMap() );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/game-settings?saved" );
    }

    /**
     * Verifies that a rejection by the service shows its message.
     */
    @Test
    void shouldShowServiceMessage_whenUpdateIsRejected() {
        // GIVEN
        GameSettingsForm form = submittedForm();
        given( launchProfileService.updateGameSettings( form ) )
                .willReturn( LaunchProfileUpdateResult.rejected( LaunchProfileMessage.SAVE_FAILED ) );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.updateGameSettings( form, new BeanPropertyBindingResult( form, "form" ), model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "gameSettingsErrorKey" ) ).isEqualTo( LaunchProfileMessage.SAVE_FAILED.getKey() );
    }

    /**
     * Verifies that a stored visibility choice is answered with an empty success response.
     */
    @Test
    void shouldReturnNoContent_whenAdvancedVisibilityIsStored() {
        // GIVEN
        given( displayService.setAdvancedShown( true ) ).willReturn( true );

        // WHEN
        ResponseEntity< Void > result = underTest.updateAdvancedShown( true );

        // THEN
        assertThat( result.getStatusCode() ).isEqualTo( HttpStatus.NO_CONTENT );
    }

    /**
     * Verifies that a visibility choice which could not be written is reported as a server error.
     */
    @Test
    void shouldReturnServerError_whenAdvancedVisibilityCannotBeStored() {
        // GIVEN
        given( displayService.setAdvancedShown( false ) ).willReturn( false );

        // WHEN
        ResponseEntity< Void > result = underTest.updateAdvancedShown( false );

        // THEN
        assertThat( result.getStatusCode() ).isEqualTo( HttpStatus.INTERNAL_SERVER_ERROR );
    }

    /**
     * Verifies that the constructor rejects a missing display service.
     */
    @Test
    void shouldThrowException_whenDisplayServiceIsNull() {
        // GIVEN
        // no display service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new GameSettingsController( launchProfileService, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "displayService must not be null" );
    }

    /**
     * Verifies that a submission without a form is rejected before the service is called.
     */
    @Test
    void shouldThrowException_whenFormIsNull() {
        // GIVEN
        BindingResult bindingResult = new BeanPropertyBindingResult( submittedForm(), "form" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( null, bindingResult,
                new ExtendedModelMap() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "gameSettingsForm must not be null" );
        verify( launchProfileService, never() ).updateGameSettings( any() );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static LaunchProfile storedProfile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, false, "", "valheim-data", 1800, 4,
                7200, 43200, WorldPreset.EASY, Map.of(), Set.of() );
    }

    private static GameSettingsForm submittedForm() {
        return new GameSettingsForm( "hard", "", "", "", "", "", null, true, null, null );
    }
}
