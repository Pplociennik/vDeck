package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Settings stored in the external {@code vDeck.properties} file next to {@code vdeck.jar}.
 * These are the panel's own, user-editable settings; Spring Boot infrastructure configuration stays
 * in {@code application.yaml} and is not represented here.
 */
public enum VdeckProperty implements SystemPropertyIf {

    /**
     * Directory holding the game server log file, relative to the installation directory.
     */
    GAME_LOG_DIRECTORY( "game.log.directory", "vdeck-logs" );

    /**
     * Name of the properties file owning every setting declared by this enum.
     */
    public static final String FILE_NAME = "vDeck.properties";

    private final String name;

    private final String defaultValue;

    VdeckProperty( @Nonnull String name, @Nonnull String defaultValue ) {
        this.name = requireNonNull( name, "name must not be null" );
        this.defaultValue = requireNonNull( defaultValue, "defaultValue must not be null" );
    }

    /**
     * Returns the default value of every setting declared by this enum, keyed by the setting itself.
     * The result is used to bootstrap a missing {@code vDeck.properties} file and to repair one that
     * lost a key, so it always describes the complete set of managed settings.
     *
     * @return unmodifiable map of settings to their default values, in declaration order
     */
    public static @Nonnull Map< SystemPropertyIf, String > defaults() {
        Map< SystemPropertyIf, String > defaults = new LinkedHashMap<>();
        for ( VdeckProperty property : values() ) {
            defaults.put( property, property.defaultValue );
        }
        return Collections.unmodifiableMap( defaults );
    }

    @Override
    public @Nonnull String getName() {
        return name;
    }

    /**
     * Returns the value used when this setting is missing or blank in the properties file.
     *
     * @return the default value of this setting, never blank
     */
    public @Nonnull String getDefaultValue() {
        return defaultValue;
    }
}
