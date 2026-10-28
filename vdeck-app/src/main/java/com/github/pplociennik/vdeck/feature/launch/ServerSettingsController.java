package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.dto.LaunchProfileForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerBackupsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerIdentityForm;
import com.github.pplociennik.vdeck.feature.launch.dto.WorldSaveForm;
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
 * Renders and processes the four panels of the "Serwer" branch of the side menu, each editing one part of
 * the launch profile: "Serwer" (server identity), "Dostęp" (server password), "Świat i zapis" (world and
 * saving) and "Kopie automatyczne" (automatic backups). Every panel stores only its own settings, and the
 * settings only take effect at the next server start.
 *
 * <p>All panels share one view, which receives the panel name and shows that panel's fields. The name
 * prefixes the panel's translation keys and is lowerCamelCase, while the address is lowercase and
 * hyphenated, so the view receives the address separately. The stored
 * server password is never sent to the browser: the "Dostęp" panel only learns whether one is set. A
 * successful update redirects back to the panel, so reloading the page never repeats the post. A rejected
 * or failed update ends with a {@link LaunchProfileUpdateException}, which
 * {@link LaunchProfileExceptionHandler} answers by redirecting back with the message and the submitted
 * values, the password excluded.
 */
@Controller
class ServerSettingsController {

    /**
     * Common beginning of the addresses of all panels; a lowercase, hyphenated segment naming the panel
     * follows it.
     */
    private static final String PATH_PREFIX = "/server-settings/";

    /**
     * Name of the "Serwer" panel, editing the server name, port, visibility and crossplay backend. A panel
     * name prefixes the panel's translation keys, so it is lowerCamelCase.
     */
    private static final String IDENTITY_PANEL = "server";

    /**
     * Address of the "Serwer" panel.
     */
    private static final String IDENTITY_PATH = PATH_PREFIX + "server";

    /**
     * Name of the "Dostęp" panel, editing the server password.
     */
    private static final String ACCESS_PANEL = "access";

    /**
     * Address of the "Dostęp" panel.
     */
    private static final String ACCESS_PATH = PATH_PREFIX + "access";

    /**
     * Name of the "Świat i zapis" panel, editing the world, the save directory and the save interval.
     */
    private static final String WORLD_PANEL = "world";

    /**
     * Address of the "Świat i zapis" panel.
     */
    private static final String WORLD_PATH = PATH_PREFIX + "world";

    /**
     * Name of the "Kopie automatyczne" panel, editing the automatic backups made by the Valheim server
     * itself. The name keeps them apart from the backups the panel makes on its own.
     */
    private static final String INNER_SERVER_BACKUPS_PANEL = "innerServerBackups";

    /**
     * Address of the "Kopie automatyczne" panel.
     */
    private static final String INNER_SERVER_BACKUPS_PATH = PATH_PREFIX + "inner-server-backups";

    /**
     * View shared by all panels; it shows the fields of the panel named by {@link #PANEL_ATTRIBUTE}.
     */
    private static final String SERVER_SETTINGS_VIEW = "launch/server-settings";

    /**
     * Model attribute naming the panel shown by the view.
     */
    private static final String PANEL_ATTRIBUTE = "panel";

    /**
     * Model attribute holding the address the panel's form is submitted to.
     */
    private static final String PANEL_PATH_ATTRIBUTE = "panelPath";

    /**
     * Model attribute holding the form of the "Serwer" panel.
     */
    private static final String IDENTITY_FORM_ATTRIBUTE = "serverIdentityForm";

    /**
     * Model attribute holding the form of the "Dostęp" panel.
     */
    private static final String ACCESS_FORM_ATTRIBUTE = "serverAccessForm";

    /**
     * Model attribute holding the form of the "Świat i zapis" panel.
     */
    private static final String WORLD_FORM_ATTRIBUTE = "worldSaveForm";

    /**
     * Model attribute holding the form of the "Kopie automatyczne" panel.
     */
    private static final String BACKUPS_FORM_ATTRIBUTE = "serverBackupsForm";

    /**
     * Model attribute telling the "Dostęp" panel whether a server password is stored, without revealing it.
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
     * Displays the "Serwer" panel filled with the stored server identity. After a rejected update the
     * model already holds the submitted values, flashed by {@link LaunchProfileExceptionHandler}, and they
     * are shown instead.
     *
     * @param model
     *         MVC model receiving the panel name and the form
     * @return server settings template name
     */
    @GetMapping( IDENTITY_PATH )
    @Nonnull
    String showServerIdentity( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        return showPanel( model, IDENTITY_PANEL, IDENTITY_PATH, IDENTITY_FORM_ATTRIBUTE,
                ServerIdentityForm.from( launchProfileService.getProfile() ) );
    }

    /**
     * Validates and stores the submitted server identity.
     *
     * @param serverIdentityForm
     *         submitted server identity
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the panel with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( IDENTITY_PATH )
    @Nonnull
    String updateServerIdentity(
            @Valid @ModelAttribute( IDENTITY_FORM_ATTRIBUTE ) @Nonnull ServerIdentityForm serverIdentityForm,
            @Nonnull BindingResult bindingResult ) {
        requireNonNull( serverIdentityForm, "serverIdentityForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        rejectIfInvalid( serverIdentityForm, bindingResult );
        launchProfileService.updateServerIdentity( serverIdentityForm );
        return savedRedirect( IDENTITY_PATH );
    }

    /**
     * Displays the "Dostęp" panel with an empty password field and the information whether a password is
     * stored. After a rejected update the model already holds the submitted form without its password.
     *
     * @param model
     *         MVC model receiving the panel name, the form and the password state
     * @return server settings template name
     */
    @GetMapping( ACCESS_PATH )
    @Nonnull
    String showServerAccess( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        model.addAttribute( PASSWORD_SET_ATTRIBUTE, launchProfileService.getProfile().passwordSet() );
        return showPanel( model, ACCESS_PANEL, ACCESS_PATH, ACCESS_FORM_ATTRIBUTE, ServerAccessForm.empty() );
    }

    /**
     * Validates and stores the submitted server password.
     *
     * @param serverAccessForm
     *         submitted access settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the panel with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( ACCESS_PATH )
    @Nonnull
    String updateServerAccess(
            @Valid @ModelAttribute( ACCESS_FORM_ATTRIBUTE ) @Nonnull ServerAccessForm serverAccessForm,
            @Nonnull BindingResult bindingResult ) {
        requireNonNull( serverAccessForm, "serverAccessForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        rejectIfInvalid( serverAccessForm, bindingResult );
        launchProfileService.updateServerAccess( serverAccessForm );
        return savedRedirect( ACCESS_PATH );
    }

    /**
     * Displays the "Świat i zapis" panel filled with the stored world and save settings, or with the
     * submitted values flashed after a rejected update.
     *
     * @param model
     *         MVC model receiving the panel name and the form
     * @return server settings template name
     */
    @GetMapping( WORLD_PATH )
    @Nonnull
    String showWorldSave( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        return showPanel( model, WORLD_PANEL, WORLD_PATH, WORLD_FORM_ATTRIBUTE,
                WorldSaveForm.from( launchProfileService.getProfile() ) );
    }

    /**
     * Validates and stores the submitted world and save settings.
     *
     * @param worldSaveForm
     *         submitted world and save settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the panel with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( WORLD_PATH )
    @Nonnull
    String updateWorldSave( @Valid @ModelAttribute( WORLD_FORM_ATTRIBUTE ) @Nonnull WorldSaveForm worldSaveForm,
                            @Nonnull BindingResult bindingResult ) {
        requireNonNull( worldSaveForm, "worldSaveForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        rejectIfInvalid( worldSaveForm, bindingResult );
        launchProfileService.updateWorldSave( worldSaveForm );
        return savedRedirect( WORLD_PATH );
    }

    /**
     * Displays the "Kopie automatyczne" panel filled with the stored backup settings, or with the
     * submitted values flashed after a rejected update.
     *
     * @param model
     *         MVC model receiving the panel name and the form
     * @return server settings template name
     */
    @GetMapping( INNER_SERVER_BACKUPS_PATH )
    @Nonnull
    String showServerBackups( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        return showPanel( model, INNER_SERVER_BACKUPS_PANEL, INNER_SERVER_BACKUPS_PATH, BACKUPS_FORM_ATTRIBUTE,
                ServerBackupsForm.from( launchProfileService.getProfile() ) );
    }

    /**
     * Validates and stores the submitted automatic backup settings.
     *
     * @param serverBackupsForm
     *         submitted backup settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the panel with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( INNER_SERVER_BACKUPS_PATH )
    @Nonnull
    String updateServerBackups(
            @Valid @ModelAttribute( BACKUPS_FORM_ATTRIBUTE ) @Nonnull ServerBackupsForm serverBackupsForm,
            @Nonnull BindingResult bindingResult ) {
        requireNonNull( serverBackupsForm, "serverBackupsForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        rejectIfInvalid( serverBackupsForm, bindingResult );
        launchProfileService.updateServerBackups( serverBackupsForm );
        return savedRedirect( INNER_SERVER_BACKUPS_PATH );
    }

    /**
     * Publishes the panel name, the address its form posts to and the stored form, unless the model already
     * holds the form flashed after a rejected update.
     */
    private static @Nonnull String showPanel( @Nonnull Model model, @Nonnull String panel, @Nonnull String path,
                                              @Nonnull String formAttribute, @Nonnull LaunchProfileForm storedForm ) {
        model.addAttribute( PANEL_ATTRIBUTE, panel );
        model.addAttribute( PANEL_PATH_ATTRIBUTE, path );
        if ( !model.containsAttribute( formAttribute ) ) {
            model.addAttribute( formAttribute, storedForm );
        }
        return SERVER_SETTINGS_VIEW;
    }

    private static void rejectIfInvalid( @Nonnull LaunchProfileForm form, @Nonnull BindingResult bindingResult ) {
        if ( bindingResult.hasErrors() ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
    }

    private static @Nonnull String savedRedirect( @Nonnull String path ) {
        return "redirect:" + path + "?saved";
    }
}
