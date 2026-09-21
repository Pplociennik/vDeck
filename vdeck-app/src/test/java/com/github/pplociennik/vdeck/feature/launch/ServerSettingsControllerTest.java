package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
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
     * Verifies that annotation failures are reported without calling the service, and that the
     * submitted password is not written back.
     */
    @Test
    void shouldReturnValidationErrorKey_whenBindingHasErrors() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ServerSettingsForm form = submittedForm();
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "serverSettingsForm" );
        bindingResult.addError( new FieldError( "serverSettingsForm", "port", "out of range" ) );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.updateServerSettings( form, bindingResult, model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "serverSettingsErrorKey" ) ).isEqualTo( LaunchProfileMessage.INVALID_INPUT.getKey() );
        assertThat( model.get( "serverSettingsForm" ) ).isEqualTo( form.withoutPassword() );
        verify( launchProfileService, never() ).updateServerSettings( any() );
    }

    /**
     * Verifies that stored settings redirect back to the screen with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenSettingsAreStored() {
        // GIVEN
        ServerSettingsForm form = submittedForm();
        given( launchProfileService.updateServerSettings( form ) ).willReturn( LaunchProfileUpdateResult.success() );

        // WHEN
        String result = underTest.updateServerSettings( form, new BeanPropertyBindingResult( form, "form" ),
                new ExtendedModelMap() );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings?saved" );
    }

    /**
     * Verifies that a rejection by the service shows its message and the submitted values without the
     * password.
     */
    @Test
    void shouldShowServiceMessage_whenUpdateIsRejected() {
        // GIVEN
        ServerSettingsForm form = submittedForm();
        given( launchProfileService.updateServerSettings( form ) )
                .willReturn( LaunchProfileUpdateResult.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME ) );
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.updateServerSettings( form, new BeanPropertyBindingResult( form, "form" ), model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "serverSettingsErrorKey" ) )
                .isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME.getKey() );
        assertThat( ( ( ServerSettingsForm ) model.get( "serverSettingsForm" ) ).password() ).isNull();
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
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( null, bindingResult,
                new ExtendedModelMap() ) );

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
