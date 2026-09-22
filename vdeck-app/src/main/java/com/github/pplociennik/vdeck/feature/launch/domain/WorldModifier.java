package com.github.pplociennik.vdeck.feature.launch.domain;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * World modifiers accepted by the {@code -modifier <name> <value>} option of the Valheim server, each
 * with the closed set of values the dedicated server manual lists for it.
 */
public enum WorldModifier {

    /**
     * Strength of enemies relative to the players.
     */
    COMBAT( "combat", LaunchProperty.WORLD_MODIFIER_COMBAT,
            List.of( "veryeasy", "easy", "hard", "veryhard" ) ),

    /**
     * What a player loses on death.
     */
    DEATH_PENALTY( "deathPenalty", LaunchProperty.WORLD_MODIFIER_DEATH_PENALTY,
            List.of( "casual", "veryeasy", "easy", "hard", "hardcore" ) ),

    /**
     * Amount of resources dropped by enemies and gathered from the world.
     */
    RESOURCES( "resources", LaunchProperty.WORLD_MODIFIER_RESOURCES,
            List.of( "muchless", "less", "more", "muchmore", "most" ) ),

    /**
     * Frequency of raids on the players' bases.
     */
    RAIDS( "raids", LaunchProperty.WORLD_MODIFIER_RAIDS,
            List.of( "none", "muchless", "less", "more", "muchmore" ) ),

    /**
     * Which items may be carried through portals.
     */
    PORTALS( "portals", LaunchProperty.WORLD_MODIFIER_PORTALS,
            List.of( "casual", "hard", "veryhard" ) );

    private final String key;

    private final LaunchProperty property;

    private final List< String > allowedValues;

    WorldModifier( @Nonnull String key, @Nonnull LaunchProperty property, @Nonnull List< String > allowedValues ) {
        this.key = requireNonNull( key, "key must not be null" );
        this.property = requireNonNull( property, "property must not be null" );
        this.allowedValues = List.copyOf( allowedValues );
    }

    /**
     * Reports whether the value is one the server accepts for this modifier. The comparison is exact,
     * because the stored and submitted values are always the lower-case command-line values.
     *
     * @param value
     *         value to check; {@code null} is never allowed
     * @return {@code true} when the value belongs to this modifier's value set
     */
    public boolean isAllowed( @Nullable String value ) {
        return value != null && allowedValues.contains( value );
    }

    /**
     * Returns the stable camel-case identifier of this modifier. The launch profile screen derives the
     * form field ({@code <key>Modifier}) and the translation keys of the modifier from it.
     *
     * @return camel-case identifier
     */
    public @Nonnull String getKey() {
        return key;
    }

    /**
     * Returns the launch profile setting storing the value of this modifier.
     *
     * @return owning setting of the launch profile file
     */
    public @Nonnull LaunchProperty getProperty() {
        return property;
    }

    /**
     * Returns the values the server accepts for this modifier, in the order the manual lists them.
     *
     * @return unmodifiable list of lower-case command-line values
     */
    public @Nonnull List< String > getAllowedValues() {
        return allowedValues;
    }
}