package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import jakarta.annotation.Nonnull;

import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * Distribution package of a validated GitHub release. Only {@link GitHubReleaseClient} creates it, after
 * checking the tag, the exact asset name, the size limit, the SHA-256 digest format and the download URL.
 *
 * @param version
 *         released version
 * @param name
 *         asset file name {@code vDeck-dist-<version>.zip}
 * @param size
 *         exact size in bytes declared by the API
 * @param sha256
 *         lowercase hexadecimal SHA-256 digest declared by the API
 * @param downloadUri
 *         HTTPS download URL on {@code github.com}
 */
record ReleaseAsset( @Nonnull ReleaseVersion version, @Nonnull String name, long size, @Nonnull String sha256,
                     @Nonnull URI downloadUri ) {

    /**
     * Validates the presence of the components.
     *
     * @throws NullPointerException
     *         if a reference component is null
     */
    ReleaseAsset {
        requireNonNull( version, "version must not be null" );
        requireNonNull( name, "name must not be null" );
        requireNonNull( sha256, "sha256 must not be null" );
        requireNonNull( downloadUri, "downloadUri must not be null" );
    }
}
