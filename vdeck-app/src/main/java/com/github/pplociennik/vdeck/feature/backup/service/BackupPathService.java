package com.github.pplociennik.vdeck.feature.backup.service;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;

/**
 * Resolver of the AD-018 snapshot root {@code <installation>/.vDeck/<snapshot.directory>}, whose name is
 * configured in {@code vDeck.properties}.
 */
public interface BackupPathService {

    /**
     * Resolves the configured snapshot root without creating it. Requires {@code vDeck.properties} to be
     * loaded, so it is called per launch rather than during bean creation.
     *
     * @param saveDirectory
     *         active world's absolute save directory
     * @return safe normalized directory directly below {@code .vDeck}, disjoint from active data
     * @throws NullPointerException
     *         if the directory is null
     * @throws RuntimeException
     *         if the configured name is not a single directory, the root overlaps the active world, a path
     *         redirects, or existing directories cannot be inspected
     */
    @Nonnull
    Path resolveRoot( @Nonnull Path saveDirectory );
}
