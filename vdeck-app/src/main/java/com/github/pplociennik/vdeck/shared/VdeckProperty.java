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
     * Directory holding the game server log files, relative to the installation directory. Each launch
     * writes its own file below a directory named after the day, so a restart never overwrites the log
     * that would explain why the previous run ended.
     */
    GAME_LOG_DIRECTORY( "game.log.directory", ".vDeck/log", true ),

    /**
     * How many days of game server logs are kept. Older day directories are removed before a launch, so
     * one log file per start cannot grow without bound (AD-001).
     */
    GAME_LOG_RETENTION_DAYS( "game.log.retention.days", "14", true ),

    /**
     * Name of the Valheim start script shipped with the dedicated server, in the installation
     * directory. vDeck only reads it, to pick up the environment the publisher's script sets, and never
     * writes to it — Steam would overwrite the change anyway.
     */
    SERVER_START_SCRIPT( "server.start.script", "start_headless_server.bat", true ),

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