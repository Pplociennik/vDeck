package com.github.pplociennik.vdeck.feature.launch.domain;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Settings of the Valheim server launch profile, stored in the external {@code valheim-server.properties}
 * file in the configuration directory of the installation (AD-011). Each setting corresponds to one
 * command-line option of {@code valheim_server.exe} described in the official dedicated server manual.
 *
 * <p>Values are stored in the form the server expects on its command line, except for flags, which are
 * stored as {@code true} or {@code false} and translated when the command line is built. An empty value
 * of an optional setting means that the option is not passed at all.
 *
 * <p>The {@code -logFile} option is deliberately absent: the panel always derives it from
 * {@code game.log.directory} in {@code vDeck.properties}.
 */
public enum LaunchProperty implements SystemPropertyIf {

    /**
     * {@code -name}: server name shown in the server list.
     */
    SERVER_NAME( "server.name", "vDeck", true ),

    /**
     * {@code -port}: first of the two consecutive ports used by the server.
     */
    SERVER_PORT( "server.port", "2456", true ),

    /**
     * {@code -world}: name of the world created or loaded by the server.
     */
    SERVER_WORLD( "server.world", "Dedicated", true ),

    /**
     * {@code -password}: password players have to enter; a secret that is never logged nor rendered.
     */
    SERVER_PASSWORD( "server.password", "", false ),

    /**
     * {@code -public}: whether the server is listed in the server browser.
     */
    SERVER_PUBLIC( "server.public", "true", true ),

    /**
     * {@code -crossplay}: whether the server runs on the crossplay backend instead of Steam.
     */
    SERVER_CROSSPLAY( "server.crossplay", "false", true ),

    /**
     * {@code -instanceid}: identifier distinguishing several crossplay servers on one machine and port.
     */
    SERVER_INSTANCE_ID( "server.instance.id", "", false ),

    /**
     * {@code -savedir}: directory of worlds and player lists, relative to the installation directory.
     */
    SERVER_SAVE_DIRECTORY( "server.save.directory", "valheim-data", true ),

    /**
     * {@code -saveinterval}: interval between automatic world saves, in seconds.
     */
    SERVER_SAVE_INTERVAL( "server.save.interval", "1800", true ),

    /**
     * {@code -backups}: number of automatic backups kept by the server.
     */
    SERVER_BACKUP_COUNT( "server.backup.count", "4", true ),

    /**
     * {@code -backupshort}: interval before the first automatic backup, in seconds.
     */
    SERVER_BACKUP_SHORT( "server.backup.short", "7200", true ),

    /**
     * {@code -backuplong}: interval between the subsequent automatic backups, in seconds.
     */
    SERVER_BACKUP_LONG( "server.backup.long", "43200", true ),

    /**
     * {@code -preset}: world modifier preset; empty when no preset is applied.
     */
    WORLD_PRESET( "world.preset", "", false ),

    /**
     * {@code -modifier combat}: combat difficulty; empty keeps the game default.
     */
    WORLD_MODIFIER_COMBAT( "world.modifier.combat", "", false ),

    /**
     * {@code -modifier deathpenalty}: death penalty; empty keeps the game default.
     */
    WORLD_MODIFIER_DEATH_PENALTY( "world.modifier.death.penalty", "", false ),

    /**
     * {@code -modifier resources}: resource rate; empty keeps the game default.
     */
    WORLD_MODIFIER_RESOURCES( "world.modifier.resources", "", false ),

    /**
     * {@code -modifier raids}: raid frequency; empty keeps the game default.
     */
    WORLD_MODIFIER_RAIDS( "world.modifier.raids", "", false ),

    /**
     * {@code -modifier portals}: portal restrictions; empty keeps the game default.
     */
    WORLD_MODIFIER_PORTALS( "world.modifier.portals", "", false ),

    /**
     * {@code -setkey nobuildcost}: building costs no resources.
     */
    WORLD_KEY_NO_BUILD_COST( "world.key.no.build.cost", "false", true ),

    /**
     * {@code -setkey playerevents}: raids depend on the progress of the individual players.
     */
    WORLD_KEY_PLAYER_EVENTS( "world.key.player.events", "false", true ),

    /**
     * {@code -setkey passivemobs}: enemies do not attack first.
     */
    WORLD_KEY_PASSIVE_MOBS( "world.key.passive.mobs", "false", true ),

    /**
     * {@code -setkey nomap}: the map and the minimap are disabled.
     */
    WORLD_KEY_NO_MAP( "world.key.no.map", "false", true );

    /**
     * Name of the properties file owning every setting declared by this enum.
     */
    public static final String FILE_NAME = "valheim-server.properties";

    /**
     * Name of the bean serving {@link #FILE_NAME}; consumers inject that service with this qualifier,
     * because every properties file has its own service bean.
     */
    public static final String SERVICE_QUALIFIER = "launchProperties";

    private final String name;

    private final String defaultValue;

    private final boolean mandatory;

    LaunchProperty( @Nonnull String name, @Nonnull String defaultValue, boolean mandatory ) {
        this.name = requireNonNull( name, "name must not be null" );
        this.defaultValue = requireNonNull( defaultValue, "defaultValue must not be null" );
        this.mandatory = mandatory;
    }

    /**
     * Returns the default value of every setting declared by this enum, keyed by the setting itself.
     * The result bootstraps a missing {@code valheim-server.properties} file and repairs one that lost a
     * key, so it always describes the complete set of managed settings.
     *
     * @return unmodifiable map of settings to their default values, in declaration order
     */
    public static @Nonnull Map< SystemPropertyIf, String > defaults() {
        Map< SystemPropertyIf, String > defaults = new LinkedHashMap<>();
        for ( LaunchProperty property : values() ) {
            defaults.put( property, property.defaultValue );
        }
        return Collections.unmodifiableMap( defaults );
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
     * @return the default value of this setting; empty for an optional setting that is unused by default
     */
    public @Nonnull String getDefaultValue() {
        return defaultValue;
    }
}