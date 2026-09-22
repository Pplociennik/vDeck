package com.github.pplociennik.vdeck.feature.launch.service.impl;

import com.github.pplociennik.vdeck.feature.launch.service.GameSettingsDisplayService;
import com.github.pplociennik.vdeck.shared.PropertyReader;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;

/**
 * Keeps the visibility of the advanced section of the "Ustawienia gry" screen in {@code vDeck.properties},
 * under {@link VdeckProperty#GAME_SETTINGS_ADVANCED}. A stored value other than {@code true} or
 * {@code false} is treated as hidden and logged by {@link PropertyReader}.
 */
@Log4j2
@Service
class GameSettingsDisplayServiceImpl implements GameSettingsDisplayService {

    private final SystemPropertiesService systemPropertiesService;

    /**
     * Creates the service storing the display state in the panel's own settings file.
     *
     * @param systemPropertiesService
     *         service reading and writing {@code vDeck.properties}
     */
    GameSettingsDisplayServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
    }

    @Override
    public boolean isAdvancedShown() {
        return PropertyReader.readBoolean( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_SETTINGS_ADVANCED, Boolean.FALSE.toString() );
    }

    @Override
    public void setAdvancedShown( boolean shown ) {
        systemPropertiesService.setValue( VdeckProperty.FILE_NAME, VdeckProperty.GAME_SETTINGS_ADVANCED,
                Boolean.toString( shown ) );
        log.info( "Advanced game settings {}", shown ? "shown" : "hidden" );
    }
}
