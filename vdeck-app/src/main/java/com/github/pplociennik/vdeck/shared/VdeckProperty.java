package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Settings stored in the external {@code vDeck.properties} file in the configuration directory of the
 * installation. These are the panel's own, user-editable settings; Spring Boot infrastructure
 * configuration stays in {@code application.yaml} and is not represented here.
 */
public enum VdeckProperty implements SystemPropertyIf {

    /**
     * Directory holding the game server log file, relative to the installation directory.
     */
    GAME_LOG_DIRECTORY( "game.log.directory", "vdeck-logs", true ),

    /**
     * Whether the "Ustawienia gry" screen shows its advanced section; {@code true} or {@code false}.
     * Remembered so the section keeps the visibility the administrator last chose.
     */
    GAME_SETTINGS_ADVANCED( "panel.game.settings.advanced", "false", true );

    /**
     * Name of the properties file owning every setting declared by this enum.
     */
    public static final String FILE_NAME = "vDeck.properties";

    private final String name;

    private final String defaultValue;

    private final boolean mandatory;

    VdeckProperty( @Nonnull String name, @Nonnull String defaultValue, boolean mandatory ) {
        this.name = requireNonNull( name, "name must not be null" );
        this.defaultValue = requireNonNull( defaultValue, "defaultValue must not be null" );
        this.mandatory = mandatory;
    }

    /**
     * Returns the default value of every setting declared by this enum, as one set of setting values.
     * The result is used to bootstrap a missing {@code vDeck.properties} file and to repair one that
     * lost a key, so it always describes the complete set of managed settings.
     *
     * @return new set of every setting with its default value, in declaration order
     */
    public static @Nonnull SystemPropertyValues defaults() {
        SystemPropertyValues defaults = SystemPropertyValues.create();
        for ( VdeckProperty property : values() ) {
            defaults.with( property, property.defaultValue );
        }
        return defaults;
    }

    @Override
    public @Nonnull String getName() {
        return name;
    }

    @Override
    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * Returns the value used when this setting is missing in the properties file, or blank while the
     * setting is mandatory.
     *
     * @return the default value of this setting
     */
    public @Nonnull String getDefaultValue() {
        return defaultValue;
    }
}