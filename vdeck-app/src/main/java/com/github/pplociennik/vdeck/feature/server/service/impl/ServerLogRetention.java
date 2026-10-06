package com.github.pplociennik.vdeck.feature.server.service.impl;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Removes the day directories of game server logs that fell out of the retention window.
 *
 * <p>Only directories named after a date are considered; anything else in the log directory belongs to
 * someone else and is left alone. Whether retention runs at all and how long the window is are decided by
 * the caller from the panel settings. The methods are static and stateless.
 */
@Log4j2
final class ServerLogRetention {

    private ServerLogRetention() {
    }

    /**
     * Removes every day directory dated before {@code oldestKept}; the day {@code oldestKept} itself and
     * later days are kept.
     *
     * <p>The cleanup is best effort and never throws: a directory that cannot be listed or removed is
     * logged at {@code WARN}, because losing disk space is a smaller problem than refusing to start the
     * server.
     *
     * @param baseDirectory
     *         existing game log directory holding the day directories
     * @param oldestKept
     *         oldest day whose logs are kept
     */
    static void removeDaysBefore( @Nonnull Path baseDirectory, @Nonnull LocalDate oldestKept ) {
        try ( Stream< Path > entries = Files.list( baseDirectory ) ) {
            entries.filter( Files::isDirectory )
                    .filter( directory -> isExpired( directory, oldestKept ) )
                    .forEach( ServerLogDirectories::deleteRecursively );
        } catch ( IOException exception ) {
            log.warn( "Could not apply game log retention in {}: {}", baseDirectory, exception.getMessage() );
        }
    }

    /**
     * Tells whether a directory is a day directory older than the retention window. A directory whose
     * name is not a date is never expired.
     */
    private static boolean isExpired( @Nonnull Path directory, @Nonnull LocalDate oldestKept ) {
        Optional< LocalDate > day = ServerLogDirectories.dayOf( directory );
        if ( day.isEmpty() ) {
            log.debug( "Ignoring {} during game log retention: not a day directory", directory );
            return false;
        }
        return day.get().isBefore( oldestKept );
    }
}
