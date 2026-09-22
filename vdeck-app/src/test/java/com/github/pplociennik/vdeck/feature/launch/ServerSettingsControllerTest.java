package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies MVC behavior of {@link ServerSettingsController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class ServerSettingsControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String VIEW = "launch/server-settings";

    @Mock
    private LaunchProfileService launchProfileService;

    private ServerSettingsController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated launch profile service.
     */
    @BeforeEach
    void setUp() {
        underTest = new ServerSettingsController( launchProfileService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that opening the screen fills the form from the stored profile, publishes whether a
     * password is set and never publishes the password itself.
     */
    @Test
    void shouldFillFormFromStoredProfile_whenPageIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showServerSettings( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "serverSettingsForm" ) ).isEqualTo( ServerSettingsForm.from( storedProfile() ) );
        assertThat( model.get( "passwordSet" ) ).isEqualTo( true );
    }

    /**
     * Verifies that the submitted values flashed after a rejected update are shown instead of the stored
     * profile.
     */
    @Test
    void shouldKeepFlashedForm_whenPageIsOpenedAfterRejection() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ServerSettingsForm flashed = submittedForm().withoutPassword();
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute( "serverSettingsForm", flashed );

        // WHEN
        String result = underTest.showServerSettings( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "serverSettingsForm" ) ).isSameAs( flashed );
        assertThat( model.get( "passwordSet" ) ).isEqualTo( true );
    }

    /**
     * Verifies that annotation failures end the update with the process exception without calling the
     * service, and that the exception keeps the submitted values without the password.
     */
    @Test
    void shouldThrowInvalidInput_whenBindingHasErrors() {
        // GIVEN
        ServerSettingsForm form = submittedForm();
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "serverSettingsForm" );
        bindingResult.addError( new FieldError( "serverSettingsForm", "port", "out of range" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form, bindingResult ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT );
            assertThat( exception.getSubmittedForm() ).isEqualTo( form.withoutPassword() );
        } );
        verify( launchProfileService, never() ).updateServerSettings( any() );
    }

    /**
     * Verifies that stored settings redirect back to the screen with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenSettingsAreStored() {
        // GIVEN
        ServerSettingsForm form = submittedForm();

        // WHEN
        String result = underTest.updateServerSettings( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings?saved" );
        verify( launchProfileService ).updateServerSettings( form );
    }

    /**
     * Verifies that a rejection by the service is left to the controller advice instead of being handled
     * here.
     */
    @Test
    void shouldPropagateException_whenServiceRejectsUpdate() {
        // GIVEN
        ServerSettingsForm form = submittedForm();
        LaunchProfileUpdateException rejection =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        willThrow( rejection ).given( launchProfileService ).updateServerSettings( form );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.updateServerSettings( form, new BeanPropertyBindingResult( form, "form" ) ) );

        // THEN
        assertThat( thrown ).isSameAs( rejection );
    }

    /**
     * Verifies that the constructor rejects a missing service.
     */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerSettingsController( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "launchProfileService must not be null" );
    }

    /**
     * Verifies that a submission without a form is rejected before the service is called.
     */
    @Test
    void shouldThrowException_whenFormIsNull() {
        // GIVEN
        BindingResult bindingResult = new BeanPropertyBindingResult( submittedForm(), "form" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( null, bindingResult ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "serverSettingsForm must not be null" );
        verify( launchProfileService, never() ).updateServerSettings( any() );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static LaunchProfile storedProfile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, false, "", "valheim-data", 1800, 4,
                7200, 43200, null, Map.of(), Set.of() );
    }

    private static ServerSettingsForm submittedForm() {
        return new ServerSettingsForm( "vDeck", 2456, "Dedicated", "Secret1", false, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200 );
    }
}
