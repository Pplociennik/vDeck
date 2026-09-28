package com.github.pplociennik.vdeck.feature.launch.domain;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * One immutable version of the launch settings, shared by validation and command generation.
 * The profile is safe for the web layer; this configuration also contains the password and must
 * never be placed in a web model or serialized. It is replaced after a successful settings update.
 *
 * @param profile
 *         secret-free typed settings from the same version as the password
 * @param password
 *         server password, empty when password protection is disabled
 */
public record LaunchConfiguration( @Nonnull LaunchProfile profile, @Nonnull String password ) {

    /**
     * Creates a complete configuration without copying the immutable profile.
     *
     * @throws NullPointerException
     *         if either component is null
     */
    public LaunchConfiguration {
        requireNonNull( profile, "profile must not be null" );
        requireNonNull( password, "password must not be null" );
    }

    /**
     * Redacts the configuration so accidental logging cannot disclose the password.
     *
     * @return a constant, secret-free description
     */
    @Override
    public @Nonnull String toString() {
        return "LaunchConfiguration[<redacted>]";
    }
}
