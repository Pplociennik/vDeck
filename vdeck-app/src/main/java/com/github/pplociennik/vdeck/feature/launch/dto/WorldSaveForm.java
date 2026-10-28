package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static java.util.Objects.requireNonNull;

/**
 * Carries the fields submitted from the "Świat i zapis" panel: the world, the directory it is saved in
 * and the interval of automatic saves. Annotations validate the format and range of every field; the save
 * directory is resolved and checked against the installation directory by the launch profile service.
 *
 * @param worldName
 *         world name; letters, digits, spaces, underscores and hyphens, without surrounding spaces
 * @param saveDirectory
 *         directory of worlds and player lists, relative to the installation directory
 * @param saveInterval
 *         interval between automatic world saves, from 60 to 86400 seconds
 */
public record WorldSaveForm(
        @Nullable @NotBlank @Size( max = 64 )
        @Pattern( regexp = "[A-Za-z0-9_-]([A-Za-z0-9 _-]*[A-Za-z0-9_-])?" ) String worldName,
        @Nullable @NotBlank @Size( max = 255 ) String saveDirectory,
        @Nullable @NotNull @Min( 60 ) @Max( 86_400 ) Integer saveInterval ) implements LaunchProfileForm {

    /**
     * Creates the form showing a stored profile.
     *
     * @param profile
     *         stored launch profile
     * @return form pre-filled with the world and save settings of the profile
     */
    public static @Nonnull WorldSaveForm from( @Nonnull LaunchProfile profile ) {
        requireNonNull( profile, "profile must not be null" );
        return new WorldSaveForm( profile.worldName(), profile.saveDirectory(), profile.saveInterval() );
    }
}
