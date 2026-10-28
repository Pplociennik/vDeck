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
 * Carries the fields submitted from the "Serwer" panel: the name, port, visibility and crossplay backend
 * of the server. Annotations validate the format and range of every field; the rule that the stored
 * password may not be a part of the name is checked by the launch profile service.
 *
 * <p>An unchecked checkbox is not submitted at all, so every flag treats {@code null} as {@code false}.
 * The server name is passed on the command line, so it may not contain a double quote, which would break
 * the quoting of the argument.
 *
 * @param serverName
 *         server name shown in the server list; up to 64 characters without control characters
 * @param port
 *         first of the two consecutive ports used by the server, from 1024 to 65534
 * @param publicServer
 *         whether the server is listed in the server browser
 * @param crossplay
 *         whether the server runs on the crossplay backend
 * @param instanceId
 *         crossplay instance identifier of up to 32 letters, digits, underscores and hyphens, or empty
 */
public record ServerIdentityForm(
        @Nullable @NotBlank @Size( max = 64 ) @Pattern( regexp = "[^\\p{Cntrl}\"]*" ) String serverName,
        @Nullable @NotNull @Min( 1024 ) @Max( 65534 ) Integer port,
        @Nullable Boolean publicServer,
        @Nullable Boolean crossplay,
        @Nullable @Pattern( regexp = "[A-Za-z0-9_-]{0,32}" ) String instanceId ) implements LaunchProfileForm {

    /**
     * Creates the form showing a stored profile.
     *
     * @param profile
     *         stored launch profile
     * @return form pre-filled with the server identity of the profile
     */
    public static @Nonnull ServerIdentityForm from( @Nonnull LaunchProfile profile ) {
        requireNonNull( profile, "profile must not be null" );
        return new ServerIdentityForm( profile.serverName(), profile.port(), profile.publicServer(),
                profile.crossplay(), profile.instanceId() );
    }
}
