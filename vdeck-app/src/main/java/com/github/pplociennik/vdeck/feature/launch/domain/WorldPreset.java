package com.github.pplociennik.vdeck.feature.launch.domain;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * World modifier presets accepted by the {@code -preset} option of the Valheim server. A preset replaces
 * every modifier set before it, so individual modifiers are applied after the preset.
 */
public enum WorldPreset {

    /**
     * Standard game balance.
     */
    NORMAL( "normal" ),

    /**
     * Relaxed game focused on exploration and building.
     */
    CASUAL( "casual" ),

    /**
     * Easier combat and a milder death penalty.
     */
    EASY( "easy" ),

    /**
     * Stronger enemies and a harsher death penalty.
     */
    HARD( "hard" ),

    /**
     * The hardest combination of modifiers, including the loss of items on death.
     */
    HARDCORE( "hardcore" ),

    /**
     * Stricter portals and no map, for a more immersive journey.
     */
    IMMERSIVE( "immersive" ),

    /**
     * Building without costs and with passive enemies.
     */
    HAMMER( "hammer" );

    private final String cliValue;

    WorldPreset( @Nonnull String cliValue ) {
        this.cliValue = requireNonNull( cliValue, "cliValue must not be null" );
    }

    /**
     * Finds the preset written on the command line and in the properties file as the given value.
     * The comparison ignores letter case, because the manual lists the presets capitalised while the
     * server examples use lower case.
     *
     * @param cliValue
     *         value to look up; {@code null} finds nothing
     * @return the matching preset, or an empty result when the value names none
     */
    public static @Nonnull Optional< WorldPreset > fromCliValue( @Nullable String cliValue ) {
        if ( cliValue == null ) {
            return Optional.empty();
        }
        String strippedValue = cliValue.strip();
        for ( WorldPreset preset : values() ) {
            if ( preset.cliValue.equalsIgnoreCase( strippedValue ) ) {
                return Optional.of( preset );
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the value of this preset as passed to {@code -preset} and stored in the properties file.
     *
     * @return lower-case command-line value
     */
    public @Nonnull String getCliValue() {
        return cliValue;
    }
}