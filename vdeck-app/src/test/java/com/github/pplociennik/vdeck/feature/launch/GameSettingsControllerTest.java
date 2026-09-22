package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
     * Verifies that the submitted values flashed after a rejected update are shown instead of the stored
     * profile, which is then not read at all.
     */
    @Test
    void shouldKeepFlashedForm_whenPageIsOpenedAfterRejection() {
        // GIVEN
        GameSettingsForm flashed = submittedForm();
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute( "gameSettingsForm", flashed );

        // WHEN
        String result = underTest.showGameSettings( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "gameSettingsForm" ) ).isSameAs( flashed );
        assertThat( model ).containsKeys( "advancedShown", "worldPresets", "worldModifiers", "worldKeys" );
        verify( launchProfileService, never() ).getProfile();
    }

    /**
     * Verifies that annotation failures end the update with the process exception, carrying the submitted
     * values, without calling the service.
     */
    @Test
    void shouldThrowInvalidInput_whenBindingHasErrors() {
        // GIVEN
        GameSettingsForm form = submittedForm();
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "gameSettingsForm" );
        bindingResult.addError( new FieldError( "gameSettingsForm", "preset", "too long" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( form, bindingResult ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT );
            assertThat( exception.getSubmittedForm() ).isEqualTo( form );
        } );
        verify( launchProfileService, never() ).updateGameSettings( any() );
    }

    /**
     * Verifies that stored settings redirect back to the screen with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenSettingsAreStored() {
        // GIVEN
        GameSettingsForm form = submittedForm();

        // WHEN
        String result = underTest.updateGameSettings( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/game-settings?saved" );
        verify( launchProfileService ).updateGameSettings( form );
    }

    /**
     * Verifies that a failure reported by the service is left to the controller advice instead of being
     * handled here.
     */
    @Test
    void shouldPropagateException_whenServiceFailsUpdate() {
        // GIVEN
        GameSettingsForm form = submittedForm();
        LaunchProfileUpdateException failure = LaunchProfileUpdateException.saveFailed( form,
                new UncheckedIOException( "disk full", new IOException( "disk full" ) ) );
        willThrow( failure ).given( launchProfileService ).updateGameSettings( form );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.updateGameSettings( form, new BeanPropertyBindingResult( form, "form" ) ) );

        // THEN
        assertThat( thrown ).isSameAs( failure );
    }

    /**
     * Verifies that a stored visibility choice is answered with an empty success response.
     */
    @Test
    void shouldReturnNoContent_whenAdvancedVisibilityIsStored() {
        // GIVEN
        // a writable display state

        // WHEN
        ResponseEntity< Void > result = underTest.updateAdvancedShown( true );

        // THEN
        assertThat( result.getStatusCode() ).isEqualTo( HttpStatus.NO_CONTENT );
        verify( displayService ).setAdvancedShown( true );
    }

    /**
     * Verifies that a visibility choice which could not be written is left to the default exception
     * handler instead of being answered here.
     */
    @Test
    void shouldPropagateFailure_whenAdvancedVisibilityCannotBeStored() {
        // GIVEN
        UncheckedIOException failure = new UncheckedIOException( "disk full", new IOException( "disk full" ) );
        willThrow( failure ).given( displayService ).setAdvancedShown( false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateAdvancedShown( false ) );

        // THEN
        assertThat( thrown ).isSameAs( failure );
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
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( null, bindingResult ) );

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
