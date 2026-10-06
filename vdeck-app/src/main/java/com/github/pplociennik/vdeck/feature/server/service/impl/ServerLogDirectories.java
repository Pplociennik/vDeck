package com.github.pplociennik.vdeck.feature.server.service.impl;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Directory actions on the game server log directory: naming and recognising the directory of a day,
 * creating a directory that does not exist yet and removing a directory together with its contents.
 *
 * <p>The methods are static and stateless; every path is passed by the caller, which has already
 * confined it to the installation directory.
 */
@Log4j2
final class ServerLogDirectories {

    /** Name format of a day directory, e.g. {@code 2026-09-28}. */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    private ServerLogDirectories() {
    }

    /**
     * Returns the directory holding the logs of the launches of one day. The directory is not created.
     *
     * @param baseDirectory
     *         configured game log directory
     * @param day
     *         day whose directory is returned
     * @return {@code baseDirectory} resolved with the day in the {@code yyyy-MM-dd} form
     */
    static @Nonnull Path dayDirectory( @Nonnull Path baseDirectory, @Nonnull LocalDate day ) {
        return baseDirectory.resolve( DAY_FORMAT.format( day ) );
    }

    /**
     * Returns the day a day directory is named after.
     *
     * @param directory
     *         directory whose name is read
     * @return the day, or empty when the name is not a date — such a directory is not a day directory
     */
    static @Nonnull Optional< LocalDate > dayOf( @Nonnull Path directory ) {
        Path name = directory.getFileName();
        if ( name == null ) {
            return Optional.empty();
        }
        try {
            return Optional.of( LocalDate.parse( name.toString(), DAY_FORMAT ) );
        } catch ( DateTimeParseException exception ) {
            return Optional.empty();
        }
    }

    /**
     * Creates the directory, with any missing parents, only when it does not exist yet. An existing
     * directory and its contents are left untouched.
     *
     * @param directory
     *         directory that must exist afterwards
     * @throws UncheckedIOException
     *         if the directory does not exist and cannot be created, e.g. because a file occupies its path
     */
    static void createIfMissing( @Nonnull Path directory ) {
        if ( Files.isDirectory( directory ) ) {
            return;
        }
        try {
            Files.createDirectories( directory );
            log.debug( "Created game log directory {}", directory );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to create game log directory " + directory, exception );
        }
    }

    /**
     * Removes the directory together with everything inside it, deepest entries first. Removal is best
     * effort: an entry that cannot be removed is logged at {@code WARN} and skipped, so the directory
     * may remain partly in place. Nothing is thrown.
     *
     * @param directory
     *         directory to remove
     */
    static void deleteRecursively( @Nonnull Path directory ) {
        try ( Stream< Path > entries = Files.walk( directory ) ) {
            entries.sorted( Comparator.reverseOrder() ).forEach( ServerLogDirectories::deleteQuietly );
            log.info( "Removed game log directory {}", directory );
        } catch ( IOException | UncheckedIOException exception ) {
            log.warn( "Could not remove game log directory {}: {}", directory, exception.getMessage() );
        }
    }

    private static void deleteQuietly( @Nonnull Path entry ) {
        try {
            Files.deleteIfExists( entry );
        } catch ( IOException exception ) {
            log.warn( "Could not remove {}: {}", entry, exception.getMessage() );
        }
    }
}
