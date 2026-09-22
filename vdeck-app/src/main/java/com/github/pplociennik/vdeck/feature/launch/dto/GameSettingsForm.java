package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

import static java.util.Objects.requireNonNull;

/**
 * Carries the fields submitted from the "Ustawienia gry" screen: the world preset, the world modifiers
 * and the world keys of the launch profile. The options of the preset and the modifiers are checked by
 * the launch profile service, because the allowed values differ per modifier.
 *
 * <p>An unchecked checkbox is not submitted at all, so every key treats {@code null} as {@code false}.
 *
 * @param preset
 *         command-line value of the world preset, or empty for none
 * @param combatModifier
 *         command-line value of the combat modifier, or empty for the game default
 * @param deathPenaltyModifier
 *         command-line value of the death penalty modifier, or empty for the game default
 * @param resourcesModifier
 *         command-line value of the resources modifier, or empty for the game default
 * @param raidsModifier
 *         command-line value of the raids modifier, or empty for the game default
 * @param portalsModifier
 *         command-line value of the portals modifier, or empty for the game default
 * @param noBuildCost
 *         whether building costs no resources
 * @param playerEvents
 *         whether raids depend on the progress of the individual players
 * @param passiveMobs
 *         whether enemies do not attack first
 * @param noMap
 *         whether the map is disabled
 */
public record GameSettingsForm(
        @Nullable @Size( max = 16 ) String preset,
        @Nullable @Size( max = 16 ) String combatModifier,
        @Nullable @Size( max = 16 ) String deathPenaltyModifier,
        @Nullable @Size( max = 16 ) String resourcesModifier,
        @Nullable @Size( max = 16 ) String raidsModifier,
        @Nullable @Size( max = 16 ) String portalsModifier,
        @Nullable Boolean noBuildCost,
        @Nullable Boolean playerEvents,
        @Nullable Boolean passiveMobs,
        @Nullable Boolean noMap ) {

    /**
     * Creates the form showing a stored profile.
     *
     * @param profile
     *         stored launch profile
     * @return form pre-filled with the game settings of the profile
     */
    public static @Nonnull GameSettingsForm from( @Nonnull LaunchProfile profile ) {
        requireNonNull( profile, "profile must not be null" );
        return new GameSettingsForm(
                profile.preset() == null ? "" : profile.preset().getCliValue(),
                profile.modifiers().getOrDefault( WorldModifier.COMBAT, "" ),
                profile.modifiers().getOrDefault( WorldModifier.DEATH_PENALTY, "" ),
                profile.modifiers().getOrDefault( WorldModifier.RESOURCES, "" ),
                profile.modifiers().getOrDefault( WorldModifier.RAIDS, "" ),
                profile.modifiers().getOrDefault( WorldModifier.PORTALS, "" ),
                profile.enabledKeys().contains( WorldKey.NO_BUILD_COST ),
                profile.enabledKeys().contains( WorldKey.PLAYER_EVENTS ),
                profile.enabledKeys().contains( WorldKey.PASSIVE_MOBS ),
                profile.enabledKeys().contains( WorldKey.NO_MAP ) );
    }

    /**
     * Returns the submitted value of a world modifier.
     *
     * @param modifier
     *         modifier whose field is read
     * @return submitted command-line value, or {@code null} when the field was not submitted
     */
    public @Nullable String modifierValue( @Nonnull WorldModifier modifier ) {
        return switch ( requireNonNull( modifier, "modifier must not be null" ) ) {
            case COMBAT -> combatModifier;
            case DEATH_PENALTY -> deathPenaltyModifier;
            case RESOURCES -> resourcesModifier;
            case RAIDS -> raidsModifier;
            case PORTALS -> portalsModifier;
        };
    }

    /**
     * Reports whether a world modifier key was checked.
     *
     * @param key
     *         key whose checkbox is read
     * @return {@code true} only when the checkbox was submitted checked
     */
    public boolean isKeyEnabled( @Nonnull WorldKey key ) {
        Boolean enabled = switch ( requireNonNull( key, "key must not be null" ) ) {
            case NO_BUILD_COST -> noBuildCost;
            case PLAYER_EVENTS -> playerEvents;
            case PASSIVE_MOBS -> passiveMobs;
            case NO_MAP -> noMap;
        };
        return Boolean.TRUE.equals( enabled );
    }

    /**
     * Returns the submitted preset as a known preset.
     *
     * @return the preset, or {@code null} when none was chosen or the value names no preset
     */
    public @Nullable WorldPreset presetValue() {
        return WorldPreset.fromCliValue( preset ).orElse( null );
    }
}
