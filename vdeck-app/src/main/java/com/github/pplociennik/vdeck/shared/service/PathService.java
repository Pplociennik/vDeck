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
     * Directory, relative to the installation directory, holding every user-editable properties file of
     * the panel (AD-010). No properties file is stored directly next to {@code vdeck.jar}.
     */
    String CONFIGURATION_DIRECTORY = ".vDeck/config";

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

    /**
     * Resolves a properties file inside {@link #CONFIGURATION_DIRECTORY}. This is the only way a
     * properties file location is obtained, so every such file ends up in the same directory.
     * Neither the directory nor the file is created.
     *
     * @param fileName
     *         bare file name, without any directory part
     * @return absolute path of the file inside the configuration directory
     * @throws IllegalArgumentException
     *         if the name is blank
     * @throws PathResolutionException
     *         if the name contains a directory part, is {@code .} or {@code ..}, or the resolved path
     *         leaves the installation directory
     * @throws UncheckedIOException
     *         if the real form of an existing target cannot be read
     */
    @Nonnull
    Path getConfigurationFile( @Nonnull String fileName );
}
