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
 * Carries the fields submitted from the "Serwer" screen: the server identity, access, world and save
 * options, and the automatic backups of the launch profile. Annotations validate the format and range of
 * every field; the save directory and the rules spanning several fields are checked by the launch profile
 * service.
 *
 * <p>An empty {@code password} keeps the stored password, and {@code clearPassword} removes it. The
 * password is a secret, so it is never copied back into a rendered form and the textual representation
 * of this record redacts it. An unchecked checkbox is not submitted at all, so every flag treats
 * {@code null} as {@code false}.
 *
 * <p>Text passed to the server on its command line may not contain a double quote, which would break
 * the quoting of the argument.
 *
 * @param serverName
 *         server name shown in the server list; up to 64 characters without control characters
 * @param port
 *         first of the two consecutive ports used by the server, from 1024 to 65534
 * @param worldName
 *         world name; letters, digits, spaces, underscores and hyphens, without surrounding spaces
 * @param password
 *         new server password of 5 to 64 characters without surrounding whitespace, or empty to keep
 *         the stored one
 * @param clearPassword
 *         whether the stored password is removed
 * @param publicServer
 *         whether the server is listed in the server browser
 * @param crossplay
 *         whether the server runs on the crossplay backend
 * @param instanceId
 *         crossplay instance identifier of up to 32 letters, digits, underscores and hyphens, or empty
 * @param saveDirectory
 *         directory of worlds and player lists, relative to the installation directory
 * @param saveInterval
 *         interval between automatic world saves, from 60 to 86400 seconds
 * @param backupCount
 *         number of automatic backups kept by the server, from 1 to 100
 * @param backupShort
 *         interval before the first automatic backup, from 60 to 604800 seconds
 * @param backupLong
 *         interval between the subsequent automatic backups, from 60 to 2592000 seconds
 */
public record ServerSettingsForm(
        @Nullable @NotBlank @Size( max = 64 ) @Pattern( regexp = "[^\\p{Cntrl}\"]*" ) String serverName,
        @Nullable @NotNull @Min( 1024 ) @Max( 65534 ) Integer port,
        @Nullable @NotBlank @Size( max = 64 )
        @Pattern( regexp = "[A-Za-z0-9_-]([A-Za-z0-9 _-]*[A-Za-z0-9_-])?" ) String worldName,
        @Nullable @Pattern( regexp = "|[^\\p{Cntrl}\\s\"][^\\p{Cntrl}\"]{3,62}[^\\p{Cntrl}\\s\"]" ) String password,
        @Nullable Boolean clearPassword,
        @Nullable Boolean publicServer,
        @Nullable Boolean crossplay,
        @Nullable @Pattern( regexp = "[A-Za-z0-9_-]{0,32}" ) String instanceId,
        @Nullable @NotBlank @Size( max = 255 ) String saveDirectory,
        @Nullable @NotNull @Min( 60 ) @Max( 86_400 ) Integer saveInterval,
        @Nullable @NotNull @Min( 1 ) @Max( 100 ) Integer backupCount,
        @Nullable @NotNull @Min( 60 ) @Max( 604_800 ) Integer backupShort,
        @Nullable @NotNull @Min( 60 ) @Max( 2_592_000 ) Integer backupLong ) {

    /**
     * Creates the form showing a stored profile. The password field stays empty, because the stored
     * password is never sent to the browser.
     *
     * @param profile
     *         stored launch profile
     * @return form pre-filled with the server settings of the profile
     */
    public static @Nonnull ServerSettingsForm from( @Nonnull LaunchProfile profile ) {
        requireNonNull( profile, "profile must not be null" );
        return new ServerSettingsForm(
                profile.serverName(),
                profile.port(),
                profile.worldName(),
                null,
                false,
                profile.publicServer(),
                profile.crossplay(),
                profile.instanceId(),
                profile.saveDirectory(),
                profile.saveInterval(),
                profile.backupCount(),
                profile.backupShort(),
                profile.backupLong() );
    }

    /**
     * Returns a copy of this form without the submitted password, used when the form is shown again
     * after a rejected update so the secret is never written back into the page.
     *
     * @return the same form with an empty password field
     */
    public @Nonnull ServerSettingsForm withoutPassword() {
        return new ServerSettingsForm( serverName, port, worldName, null, clearPassword, publicServer, crossplay,
                instanceId, saveDirectory, saveInterval, backupCount, backupShort, backupLong );
    }

    /**
     * Returns a representation that never includes the submitted password.
     *
     * @return description of this form with the password redacted
     */
    @Override
    public @Nonnull String toString() {
        return "ServerSettingsForm[serverName=%s, port=%s, worldName=%s, password=<redacted>, clearPassword=%s, "
                .formatted( serverName, port, worldName, clearPassword )
                + "publicServer=%s, crossplay=%s, instanceId=%s, saveDirectory=%s, saveInterval=%s, "
                .formatted( publicServer, crossplay, instanceId, saveDirectory, saveInterval )
                + "backupCount=%s, backupShort=%s, backupLong=%s]"
                .formatted( backupCount, backupShort, backupLong );
    }
}
