package com.github.pplociennik.vdeck.shared.service;

import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Defines safe path resolution relative to the vDeck installation directory.
 */
public interface PathService {

    /**
     * Returns the vDeck installation directory in its real, absolute form.
     *
     * @return the base directory every relative path is resolved against
     */
    @Nonnull
    Path getBaseDirectory();

    /**
     * Resolves a relative path and validates that the result stays inside the installation directory.
     *
     * @param relativePath
     *         configured path relative to the installation directory
     * @return absolute path inside the installation directory
     * @throws IllegalArgumentException
     *         if the value is blank
     * @throws PathResolutionException
     *         if the value is absolute, invalid, or leaves the installation directory
     * @throws UncheckedIOException
     *         if the real form of an existing target cannot be read
     */
    @Nonnull
    Path getAbsolutePath( @Nonnull String relativePath );
}
