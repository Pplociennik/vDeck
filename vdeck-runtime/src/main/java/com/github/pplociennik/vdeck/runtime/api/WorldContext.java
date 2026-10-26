package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Secret-free world selection captured at launch; subsequent profile edits cannot redirect a snapshot.
 *
 * @param name
 *         validated file stem identifying the local world
 * @param saveDirectory
 *         absolute Valheim save directory
 * @param snapshotDirectory
 *         absolute backup root directly below the installation's {@code .vDeck} directory
 */
public record WorldContext( @Nonnull String name, @Nonnull Path saveDirectory,
                            @Nonnull Path snapshotDirectory ) {

    /**
     * Validates the world file stem and freezes normalized absolute directories.
     *
     * @param name
     *         safe local world file stem
     * @param saveDirectory
     *         absolute active Valheim save directory
     * @param snapshotDirectory
     *         separately authorized absolute backup root
     * @throws NullPointerException
     *         if any component is null
     * @throws IllegalArgumentException
     *         if a name is unsafe, a directory is relative, or backup and save directories overlap
     */
    public WorldContext {
        requireNonNull( name, "name must not be null" );
        requireNonNull( saveDirectory, "saveDirectory must not be null" );
        requireNonNull( snapshotDirectory, "snapshotDirectory must not be null" );
        if ( name.isBlank() || name.length() > 128 || name.equals( "." ) || name.equals( ".." )
                || name.endsWith( "." ) || name.endsWith( " " )
                || name.matches( "(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?" )
                || name.chars().anyMatch( character -> character < 32 || "<>:\"/\\|?*".indexOf( character ) >= 0 ) ) {
            throw new IllegalArgumentException( "Unsafe world name" );
        }
        if ( !saveDirectory.isAbsolute() || !snapshotDirectory.isAbsolute() ) {
            throw new IllegalArgumentException( "World directories must be absolute" );
        }
        saveDirectory = saveDirectory.normalize();
        snapshotDirectory = snapshotDirectory.normalize();
        if ( saveDirectory.startsWith( snapshotDirectory ) || snapshotDirectory.startsWith( saveDirectory ) ) {
            throw new IllegalArgumentException( "Save and backup directories must not overlap" );
        }
    }
}
