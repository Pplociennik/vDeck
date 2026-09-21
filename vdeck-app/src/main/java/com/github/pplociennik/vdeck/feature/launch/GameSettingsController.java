package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.service.GameSettingsDisplayService;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
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
     * Model attribute carrying the translation key of the failure message.
     */
    private static final String ERROR_KEY_ATTRIBUTE = "gameSettingsErrorKey";

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
     * Displays the form filled with the stored settings.
     *
     * @param model
     *         MVC model receiving the form, the option lists and the section visibility
     * @return game settings template name
     */
    @GetMapping( GAME_SETTINGS_PATH )
    @Nonnull
    String showGameSettings( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        model.addAttribute( FORM_ATTRIBUTE, GameSettingsForm.from( launchProfileService.getProfile() ) );
        return render( model );
    }

    /**
     * Validates and stores submitted game settings.
     *
     * @param gameSettingsForm
     *         submitted settings
     * @param bindingResult
     *         validation result produced from the form annotations
     * @param model
     *         MVC model receiving the form and the translation key of the failure message
     * @return redirect to the screen after success, otherwise the game settings template name
     */
    @PostMapping( GAME_SETTINGS_PATH )
    @Nonnull
    String updateGameSettings(
            @Valid @ModelAttribute( FORM_ATTRIBUTE ) @Nonnull GameSettingsForm gameSettingsForm,
            @Nonnull BindingResult bindingResult,
            @Nonnull Model model ) {
        requireNonNull( gameSettingsForm, "gameSettingsForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );
        requireNonNull( model, "model must not be null" );

        if ( bindingResult.hasErrors() ) {
            return renderFailure( model, LaunchProfileMessage.INVALID_INPUT );
        }

        LaunchProfileUpdateResult result = launchProfileService.updateGameSettings( gameSettingsForm );
        if ( result.isSuccessful() ) {
            return "redirect:" + GAME_SETTINGS_PATH + "?saved";
        }
        return renderFailure( model, requireNonNull( result.messageKey() ) );
    }

    /**
     * Remembers whether the advanced section is shown. Called by the screen's script whenever the
     * administrator toggles the section, independently of saving the settings.
     *
     * @param shown
     *         whether the section is shown
     * @return empty response: 204 when the choice was stored, 500 when it could not be written
     */
    @PostMapping( GAME_SETTINGS_PATH + "/advanced" )
    @Nonnull
    ResponseEntity< Void > updateAdvancedShown( @RequestParam( "shown" ) boolean shown ) {
        if ( displayService.setAdvancedShown( shown ) ) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.internalServerError().build();
    }

    /**
     * Shows the submitted values again with a single failure message, so the user does not lose the edits.
     * The rejected form stays in the model under its attribute name.
     */
    private @Nonnull String renderFailure( @Nonnull Model model, @Nonnull TranslationKeyIf messageKey ) {
        model.addAttribute( ERROR_KEY_ATTRIBUTE, messageKey.getKey() );
        return render( model );
    }

    private @Nonnull String render( @Nonnull Model model ) {
        model.addAttribute( ADVANCED_SHOWN_ATTRIBUTE, displayService.isAdvancedShown() );
        model.addAttribute( "worldPresets", WorldPreset.values() );
        model.addAttribute( "worldModifiers", WorldModifier.values() );
        model.addAttribute( "worldKeys", WorldKey.values() );
        return GAME_SETTINGS_VIEW;
    }
}
