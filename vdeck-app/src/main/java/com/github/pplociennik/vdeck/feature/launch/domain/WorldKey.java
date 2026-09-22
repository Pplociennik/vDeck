package com.github.pplociennik.vdeck.feature.launch.domain;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * World modifier checkboxes enabled through the {@code -setkey <key>} option of the Valheim server.
 */
public enum WorldKey {

    /**
     * Building costs no resources.
     */
    NO_BUILD_COST( "noBuildCost", LaunchProperty.WORLD_KEY_NO_BUILD_COST ),

    /**
     * Raids depend on the progress of the individual players instead of the whole world.
     */
    PLAYER_EVENTS( "playerEvents", LaunchProperty.WORLD_KEY_PLAYER_EVENTS ),

    /**
     * Enemies do not attack first.
     */
    PASSIVE_MOBS( "passiveMobs", LaunchProperty.WORLD_KEY_PASSIVE_MOBS ),

    /**
     * The map and the minimap are disabled.
     */
    NO_MAP( "noMap", LaunchProperty.WORLD_KEY_NO_MAP );

    private final String key;

    private final LaunchProperty property;

    WorldKey( @Nonnull String key, @Nonnull LaunchProperty property ) {
        this.key = requireNonNull( key, "key must not be null" );
        this.property = requireNonNull( property, "property must not be null" );
    }

    /**
     * Returns the stable camel-case identifier of this key. The launch profile screen uses it as the
     * name of the form field and derives the translation keys of the checkbox from it.
     *
     * @return camel-case identifier
     */
    public @Nonnull String getKey() {
        return key;
    }

    /**
     * Returns the launch profile setting storing whether this key is enabled.
     *
     * @return owning setting of the launch profile file
     */
    public @Nonnull LaunchProperty getProperty() {
        return property;
    }
}