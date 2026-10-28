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
     * Whether old game server logs are removed at all; {@code true} or {@code false}. With {@code false}
     * every log is kept and {@link #GAME_LOG_RETENTION_DAYS} is ignored (AD-016).
     */
    GAME_LOG_RETENTION_ENABLED( "game.log.retention.enabled", "true", true ),

    /**
     * How many days before today game server logs are kept while {@link #GAME_LOG_RETENTION_ENABLED} is on;
     * {@code 0} keeps every log. Older day directories are removed before a launch, so one log file per
     * start cannot grow without bound (AD-016). A negative value or one that is not a number counts as the
     * default.
     */
    GAME_LOG_RETENTION_DAYS( "game.log.retention.days", "14", true ),

    /**
     * Name of the Valheim start script shipped with the dedicated server, in the installation
     * directory. vDeck only reads it, to pick up the environment the publisher's script sets, and never
     * writes to it — Steam would overwrite the change anyway.
     */
    SERVER_START_SCRIPT( "server.start.script", "start_headless_server.bat", true ),

    /**
     * Name of the directory holding world snapshots, a single directory directly below the panel's own
     * {@code .vDeck} directory (AD-018). The value is a bare name, never a path.
     */
    SNAPSHOT_DIRECTORY( "snapshot.directory", "shutdown-snapshots", true ),

    /**
     * Whether the "Ustawienia gry" screen shows its advanced section; {@code true} or {@code false}.
     * Remembered so the section keeps the visibility the administrator last chose.
     */
    GAME_SETTINGS_ADVANCED( "panel.game.settings.advanced", "false", true ),

    /**
     * Whether the panel opens its address in the default browser once it has started from the start
     * script; {@code true} or {@code false}. Any other value counts as the default.
     */
    PANEL_OPEN_BROWSER( "panel.open.browser", "true", true ),

    /**
     * What happens to the managed Valheim server when the panel closes (AD-031): {@code stop} stops it
     * gracefully and forces it to exit when that fails, {@code graceful} only stops it gracefully and keeps a
     * process that survives, {@code leave} keeps it running for explicit adoption by the next panel. Any
     * other value counts as the default.
     */
    PANEL_CLOSE_STRATEGY( "panel.close.strategy", "stop", true ),

    /**
     * Whether old panel run logs in {@code .vDeck/logs/panel} are removed at all; {@code true} or
     * {@code false}. With {@code false} every log is kept and {@link #PANEL_LOG_RETENTION_DAYS} is ignored
     * (AD-032).
     */
    PANEL_LOG_RETENTION_ENABLED( "panel.log.retention.enabled", "true", true ),

    /**
     * How many days before today panel run logs are kept while {@link #PANEL_LOG_RETENTION_ENABLED} is on;
     * {@code 0} keeps every log. Older day directories are removed once the panel has started (AD-032). A
     * negative value or one that is not a number counts as the default.
     */
    PANEL_LOG_RETENTION_DAYS( "panel.log.retention.days", "30", true );

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