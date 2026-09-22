package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import com.github.pplociennik.vdeck.feature.launch.service.GameSettingsDisplayService;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static java.util.Objects.requireNonNull;

/**
 * Renders and processes the "Ustawienia gry" screen: the world preset at the top and, in an advanced
 * section, the world modifiers and world keys of the launch profile. The settings only take effect at
 * the next server start.
 *
 * <p>The advanced section is hidden by default. Whether it is shown is remembered as soon as the
 * administrator toggles it, through a separate request, so the choice holds on every later visit.
 * Hiding the section never changes the stored settings.
 *
 * <p>A rejected or failed update ends with a {@link LaunchProfileUpdateException}, which
 * {@link LaunchProfileExceptionHandler} answers by redirecting back with the message and the submitted
 * values. A failure to remember the section visibility is left to the application-wide default handler.
 */
@Controller
class GameSettingsController {

    private static final String GAME_SETTINGS_PATH = "/game-settings";

    private static final String GAME_SETTINGS_VIEW = "launch/game-settings";

    /**
     * Model attribute holding the form backing object.
     */
    private static final String FORM_ATTRIBUTE = "gameSettingsForm";

    /**
     * Model attribute telling the view whether the advanced section starts shown.
     */
    private static final String ADVANCED_SHOWN_ATTRIBUTE = "advancedShown";

    private final LaunchProfileService launchProfileService;

    private final GameSettingsDisplayService displayService;

    /**
     * Creates the controller delegating the settings to the launch profile service and the section
     * visibility to the display service.
     *
     * @param launchProfileService
     *         service owning the launch profile
     * @param displayService
     *         service remembering whether the advanced section is shown
     */
    GameSettingsController( @Nonnull LaunchProfileService launchProfileService,
                            @Nonnull GameSettingsDisplayService displayService ) {
        this.launchProfileService = requireNonNull( launchProfileService, "launchProfileService must not be null" );
        this.displayService = requireNonNull( displayService, "displayService must not be null" );
    }

    /**
     * Displays the form filled with the stored settings. After a rejected update the model already holds
     * the submitted values, flashed by {@link LaunchProfileExceptionHandler}, and they are shown instead.
     *
     * @param model
     *         MVC model receiving the form, the option lists and the section visibility
     * @return game settings template name
     */
    @GetMapping( GAME_SETTINGS_PATH )
    @Nonnull
    String showGameSettings( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        if ( !model.containsAttribute( FORM_ATTRIBUTE ) ) {
            model.addAttribute( FORM_ATTRIBUTE, GameSettingsForm.from( launchProfileService.getProfile() ) );
        }
        model.addAttribute( ADVANCED_SHOWN_ATTRIBUTE, displayService.isAdvancedShown() );
        model.addAttribute( "worldPresets", WorldPreset.values() );
        model.addAttribute( "worldModifiers", WorldModifier.values() );
        model.addAttribute( "worldKeys", WorldKey.values() );
        return GAME_SETTINGS_VIEW;
    }

    /**
     * Validates and stores submitted game settings.
     *
     * @param gameSettingsForm
     *         submitted settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the screen with the confirmation flag
     * @throws LaunchProfileUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the settings
     *         cannot be written; nothing was stored
     */
    @PostMapping( GAME_SETTINGS_PATH )
    @Nonnull
    String updateGameSettings(
            @Valid @ModelAttribute( FORM_ATTRIBUTE ) @Nonnull GameSettingsForm gameSettingsForm,
            @Nonnull BindingResult bindingResult ) {
        requireNonNull( gameSettingsForm, "gameSettingsForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        if ( bindingResult.hasErrors() ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, gameSettingsForm );
        }
        launchProfileService.updateGameSettings( gameSettingsForm );
        return "redirect:" + GAME_SETTINGS_PATH + "?saved";
    }

    /**
     * Remembers whether the advanced section is shown. Called by the screen's script whenever the
     * administrator toggles the section, independently of saving the settings.
     *
     * @param shown
     *         whether the section is shown
     * @return empty 204 response once the choice is stored; a failed write is answered with 500 by the
     *         application-wide default handler
     */
    @PostMapping( GAME_SETTINGS_PATH + "/advanced" )
    @Nonnull
    ResponseEntity< Void > updateAdvancedShown( @RequestParam( "shown" ) boolean shown ) {
        displayService.setAdvancedShown( shown );
        return ResponseEntity.noContent().build();
    }
}
