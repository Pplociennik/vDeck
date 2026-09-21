package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import static java.util.Objects.requireNonNull;

/**
 * Renders and processes the "Serwer" screen, where the administrator defines the server identity, access,
 * world, saving and automatic backups of the launch profile. The settings only take effect at the next
 * server start.
 *
 * <p>The stored server password is never sent to the browser: the screen only learns whether one is
 * set. A successful update redirects back to the screen, so reloading the page never repeats the post.
 */
@Controller
class ServerSettingsController {

    private static final String SERVER_SETTINGS_PATH = "/server-settings";

    private static final String SERVER_SETTINGS_VIEW = "launch/server-settings";

    /**
     * Model attribute holding the form backing object.
     */
    private static final String FORM_ATTRIBUTE = "serverSettingsForm";

    /**
     * Model attribute carrying the translation key of the failure message.
     */
    private static final String ERROR_KEY_ATTRIBUTE = "serverSettingsErrorKey";

    /**
     * Model attribute telling the view whether a server password is stored, without revealing it.
     */
    private static final String PASSWORD_SET_ATTRIBUTE = "passwordSet";

    private final LaunchProfileService launchProfileService;

    /**
     * Creates the controller delegating reading and storing of the settings to the launch profile service.
     *
     * @param launchProfileService
     *         service owning the launch profile
     */
    ServerSettingsController( @Nonnull LaunchProfileService launchProfileService ) {
        this.launchProfileService = requireNonNull( launchProfileService, "launchProfileService must not be null" );
    }

    /**
     * Displays the form filled with the stored settings.
     *
     * @param model
     *         MVC model receiving the form
     * @return server settings template name
     */
    @GetMapping( SERVER_SETTINGS_PATH )
    @Nonnull
    String showServerSettings( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        LaunchProfile profile = launchProfileService.getProfile();
        model.addAttribute( FORM_ATTRIBUTE, ServerSettingsForm.from( profile ) );
        return render( model, profile );
    }

    /**
     * Validates and stores submitted server settings.
     *
     * @param serverSettingsForm
     *         submitted settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @param model
     *         MVC model receiving the form and the translation key of the failure message
     * @return redirect to the screen after success, otherwise the server settings template name
     */
    @PostMapping( SERVER_SETTINGS_PATH )
    @Nonnull
    String updateServerSettings(
            @Valid @ModelAttribute( FORM_ATTRIBUTE ) @Nonnull ServerSettingsForm serverSettingsForm,
            @Nonnull BindingResult bindingResult,
            @Nonnull Model model ) {
        requireNonNull( serverSettingsForm, "serverSettingsForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );
        requireNonNull( model, "model must not be null" );

        if ( bindingResult.hasErrors() ) {
            return renderFailure( model, serverSettingsForm, LaunchProfileMessage.INVALID_INPUT );
        }

        LaunchProfileUpdateResult result = launchProfileService.updateServerSettings( serverSettingsForm );
        if ( result.isSuccessful() ) {
            return "redirect:" + SERVER_SETTINGS_PATH + "?saved";
        }
        return renderFailure( model, serverSettingsForm, requireNonNull( result.messageKey() ) );
    }

    /**
     * Shows the submitted values again with a single failure message, so the user does not lose the
     * edits, except for the password, which is never written back into the page.
     */
    private @Nonnull String renderFailure( @Nonnull Model model, @Nonnull ServerSettingsForm form,
                                           @Nonnull TranslationKeyIf messageKey ) {
        model.addAttribute( FORM_ATTRIBUTE, form.withoutPassword() );
        model.addAttribute( ERROR_KEY_ATTRIBUTE, messageKey.getKey() );
        return render( model, launchProfileService.getProfile() );
    }

    private @Nonnull String render( @Nonnull Model model, @Nonnull LaunchProfile storedProfile ) {
        model.addAttribute( PASSWORD_SET_ATTRIBUTE, storedProfile.passwordSet() );
        return SERVER_SETTINGS_VIEW;
    }
}
