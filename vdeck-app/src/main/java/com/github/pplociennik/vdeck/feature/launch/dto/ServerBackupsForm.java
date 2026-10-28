package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Carries the fields submitted from the "Kopie automatyczne" panel: how many automatic world backups the
 * server keeps and how often it makes them. Annotations validate the range of every field.
 *
 * @param backupCount
 *         number of automatic backups kept by the server, from 1 to 100
 * @param backupShort
 *         interval before the first automatic backup, from 60 to 604800 seconds
 * @param backupLong
 *         interval between the subsequent automatic backups, from 60 to 2592000 seconds
 */
public record ServerBackupsForm(
        @Nullable @NotNull @Min( 1 ) @Max( 100 ) Integer backupCount,
        @Nullable @NotNull @Min( 60 ) @Max( 604_800 ) Integer backupShort,
        @Nullable @NotNull @Min( 60 ) @Max( 2_592_000 ) Integer backupLong ) implements LaunchProfileForm {

    /**
     * Creates the form showing a stored profile.
     *
     * @param profile
     *         stored launch profile
     * @return form pre-filled with the automatic backup settings of the profile
     */
    public static @Nonnull ServerBackupsForm from( @Nonnull LaunchProfile profile ) {
        requireNonNull( profile, "profile must not be null" );
        return new ServerBackupsForm( profile.backupCount(), profile.backupShort(), profile.backupLong() );
    }
}
