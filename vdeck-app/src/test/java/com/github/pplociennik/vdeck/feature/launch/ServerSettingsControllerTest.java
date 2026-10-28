package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerBackupsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerIdentityForm;
import com.github.pplociennik.vdeck.feature.launch.dto.WorldSaveForm;
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
import static org.mockito.Mockito.verifyNoInteractions;

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
     * Verifies that opening the "Serwer" panel names the panel and fills the form from the stored profile.
     */
    @Test
    void shouldFillIdentityFormFromStoredProfile_whenIdentityPanelIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showServerIdentity( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "panel" ) ).isEqualTo( "server" );
        assertThat( model.get( "panelPath" ) ).isEqualTo( "/server-settings/server" );
        assertThat( model.get( "serverIdentityForm" ) ).isEqualTo( ServerIdentityForm.from( storedProfile() ) );
    }

    /**
     * Verifies that the submitted values flashed after a rejected update are shown instead of the stored
     * profile.
     */
    @Test
    void shouldKeepFlashedForm_whenIdentityPanelIsOpenedAfterRejection() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ServerIdentityForm flashed = identityForm();
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute( "serverIdentityForm", flashed );

        // WHEN
        String result = underTest.showServerIdentity( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "serverIdentityForm" ) ).isSameAs( flashed );
    }

    /**
     * Verifies that opening the "Dostęp" panel publishes an empty form and whether a password is set, but
     * never the password itself.
     */
    @Test
    void shouldPublishEmptyFormAndPasswordState_whenAccessPanelIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showServerAccess( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "panel" ) ).isEqualTo( "access" );
        assertThat( model.get( "panelPath" ) ).isEqualTo( "/server-settings/access" );
        assertThat( model.get( "serverAccessForm" ) ).isEqualTo( ServerAccessForm.empty() );
        assertThat( model.get( "passwordSet" ) ).isEqualTo( true );
    }

    /**
     * Verifies that opening the "Świat i zapis" panel names the panel and fills the form from the stored
     * profile.
     */
    @Test
    void shouldFillWorldFormFromStoredProfile_whenWorldPanelIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showWorldSave( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "panel" ) ).isEqualTo( "world" );
        assertThat( model.get( "panelPath" ) ).isEqualTo( "/server-settings/world" );
        assertThat( model.get( "worldSaveForm" ) ).isEqualTo( WorldSaveForm.from( storedProfile() ) );
    }

    /**
     * Verifies that opening the "Kopie automatyczne" panel names the panel and fills the form from the
     * stored profile.
     */
    @Test
    void shouldFillBackupsFormFromStoredProfile_whenBackupsPanelIsOpened() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( storedProfile() );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showServerBackups( model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "panel" ) ).isEqualTo( "innerServerBackups" );
        assertThat( model.get( "panelPath" ) ).isEqualTo( "/server-settings/inner-server-backups" );
        assertThat( model.get( "serverBackupsForm" ) ).isEqualTo( ServerBackupsForm.from( storedProfile() ) );
    }

    /**
     * Verifies that annotation failures end the update with the process exception without calling the
     * service, and that the exception keeps the submitted values without the password.
     */
    @Test
    void shouldThrowInvalidInputWithoutPassword_whenAccessBindingHasErrors() {
        // GIVEN
        ServerAccessForm form = new ServerAccessForm( "a\"b", false );
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "serverAccessForm" );
        bindingResult.addError( new FieldError( "serverAccessForm", "password", "bad format" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerAccess( form, bindingResult ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT );
            assertThat( exception.getSubmittedForm() ).isEqualTo( new ServerAccessForm( null, false ) );
        } );
        verifyNoInteractions( launchProfileService );
    }

    /**
     * Verifies that annotation failures of another panel end the update the same way, with the submitted
     * values kept for the panel.
     */
    @Test
    void shouldThrowInvalidInput_whenIdentityBindingHasErrors() {
        // GIVEN
        ServerIdentityForm form = identityForm();
        BindingResult bindingResult = new BeanPropertyBindingResult( form, "serverIdentityForm" );
        bindingResult.addError( new FieldError( "serverIdentityForm", "port", "out of range" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerIdentity( form, bindingResult ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT );
            assertThat( exception.getSubmittedForm() ).isSameAs( form );
        } );
        verify( launchProfileService, never() ).updateServerIdentity( any() );
    }

    /**
     * Verifies that a stored server identity redirects back to its panel with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenServerIdentityIsStored() {
        // GIVEN
        ServerIdentityForm form = identityForm();

        // WHEN
        String result = underTest.updateServerIdentity( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings/server?saved" );
        verify( launchProfileService ).updateServerIdentity( form );
    }

    /**
     * Verifies that a stored password redirects back to the "Dostęp" panel with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenServerAccessIsStored() {
        // GIVEN
        ServerAccessForm form = new ServerAccessForm( "Secret1", false );

        // WHEN
        String result = underTest.updateServerAccess( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings/access?saved" );
        verify( launchProfileService ).updateServerAccess( form );
    }

    /**
     * Verifies that stored world and save settings redirect back to their panel with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenWorldSaveIsStored() {
        // GIVEN
        WorldSaveForm form = new WorldSaveForm( "Dedicated", "valheim-data", 1800 );

        // WHEN
        String result = underTest.updateWorldSave( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings/world?saved" );
        verify( launchProfileService ).updateWorldSave( form );
    }

    /**
     * Verifies that stored backup settings redirect back to their panel with the confirmation flag.
     */
    @Test
    void shouldRedirectWithConfirmation_whenServerBackupsAreStored() {
        // GIVEN
        ServerBackupsForm form = new ServerBackupsForm( 4, 7200, 43200 );

        // WHEN
        String result = underTest.updateServerBackups( form, new BeanPropertyBindingResult( form, "form" ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/server-settings/inner-server-backups?saved" );
        verify( launchProfileService ).updateServerBackups( form );
    }

    /**
     * Verifies that a rejection by the service is left to the controller advice instead of being handled
     * here.
     */
    @Test
    void shouldPropagateException_whenServiceRejectsUpdate() {
        // GIVEN
        ServerIdentityForm form = identityForm();
        LaunchProfileUpdateException rejection =
                LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        willThrow( rejection ).given( launchProfileService ).updateServerIdentity( form );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.updateServerIdentity( form, new BeanPropertyBindingResult( form, "form" ) ) );

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
     * Verifies that opening a panel without a model is rejected before the service is read.
     */
    @Test
    void shouldThrowException_whenModelIsNull() {
        // GIVEN
        // no model at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.showServerAccess( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "model must not be null" );
        verifyNoInteractions( launchProfileService );
    }

    /**
     * Verifies that a submission without a form is rejected before the service is called.
     */
    @Test
    void shouldThrowException_whenFormIsNull() {
        // GIVEN
        BindingResult bindingResult = new BeanPropertyBindingResult( identityForm(), "form" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateWorldSave( null, bindingResult ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "worldSaveForm must not be null" );
        verifyNoInteractions( launchProfileService );
    }

    /**
     * Verifies that a submission without a binding result is rejected before the service is called.
     */
    @Test
    void shouldThrowException_whenBindingResultIsNull() {
        // GIVEN
        ServerBackupsForm form = new ServerBackupsForm( 4, 7200, 43200 );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerBackups( form, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "bindingResult must not be null" );
        verifyNoInteractions( launchProfileService );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static LaunchProfile storedProfile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, false, "", "valheim-data", 1800, 4,
                7200, 43200, null, Map.of(), Set.of() );
    }

    private static ServerIdentityForm identityForm() {
        return new ServerIdentityForm( "vDeck", 2456, true, false, "" );
    }
}
