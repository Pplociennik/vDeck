package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
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
 * A rejected or failed update ends with a {@link LaunchProfileUpdateException}, which
 * {@link LaunchProfileExceptionHandler} answers by redirecting back with the message and the submitted
 * values, the password excluded.
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
     * Displays the form filled with the stored settings. After a rejected update the model already holds
     * the submitted values, flashed by {@link LaunchProfileExceptionHandler}, and they are shown instead.
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
        if ( !model.containsAttribute( FORM_ATTRIBUTE ) ) {
            model.addAttribute( FORM_ATTRIBUTE, ServerSettingsForm.from( profile ) );
        }
        model.addAttribute( PASSWORD_SET_ATTRIBUTE, profile.passwordSet() );
        return SERVER_SETTINGS_VIEW;
    }

    /**
     * Validates and stores submitted server settings.
     *
     * @param serverSettingsForm
     *         submitted settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the screen with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( SERVER_SETTINGS_PATH )
    @Nonnull
    String updateServerSettings(
            @Valid @ModelAttribute( FORM_ATTRIBUTE ) @Nonnull ServerSettingsForm serverSettingsForm,
            @Nonnull BindingResult bindingResult ) {
        requireNonNull( serverSettingsForm, "serverSettingsForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        if ( bindingResult.hasErrors() ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, serverSettingsForm );
        }
        launchProfileService.updateServerSettings( serverSettingsForm );
        return "redirect:" + SERVER_SETTINGS_PATH + "?saved";
    }
}
