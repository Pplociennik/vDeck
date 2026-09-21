package com.github.pplociennik.vdeck.feature.launch.service.impl;

import com.github.pplociennik.vdeck.feature.launch.service.GameSettingsDisplayService;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;

/**
 * Keeps the visibility of the advanced section of the "Ustawienia gry" screen in {@code vDeck.properties},
 * under {@link VdeckProperty#GAME_SETTINGS_ADVANCED}. A stored value other than {@code true} or
 * {@code false} is treated as hidden.
 */
@Log4j2
@Service
class GameSettingsDisplayServiceImpl implements GameSettingsDisplayService {

    private final SystemPropertiesService vdeckProperties;

    /**
     * Creates the service storing the display state in the panel's own settings file.
     *
     * @param vdeckProperties
     *         service owning {@code vDeck.properties}
     */
    GameSettingsDisplayServiceImpl( @Qualifier( VdeckProperty.SERVICE_QUALIFIER ) @Nonnull SystemPropertiesService vdeckProperties ) {
        this.vdeckProperties = requireNonNull( vdeckProperties, "vdeckProperties must not be null" );
    }

    @Override
    public boolean isAdvancedShown() {
        String value = vdeckProperties.getValue( VdeckProperty.GAME_SETTINGS_ADVANCED );
        if ( "true".equalsIgnoreCase( value ) || "false".equalsIgnoreCase( value ) ) {
            return Boolean.parseBoolean( value );
        }
        log.warn( "Setting '{}' is not true or false; the advanced section stays hidden",
                VdeckProperty.GAME_SETTINGS_ADVANCED.getName() );
        return false;
    }

    @Override
    public boolean setAdvancedShown( boolean shown ) {
        try {
            vdeckProperties.setValue( VdeckProperty.GAME_SETTINGS_ADVANCED, Boolean.toString( shown ) );
        } catch ( UncheckedIOException exception ) {
            log.error( "Failed to store the visibility of the advanced game settings", exception );
            return false;
        }
        log.info( "Advanced game settings {}", shown ? "shown" : "hidden" );
        return true;
    }
}
