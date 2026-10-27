package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Removes launcher or updater run logs older than {@link #RETENTION_DAYS} days. Runs only after the
 * caller holds the installation locks, so it never races another run's cleanup. A failure is logged
 * and never blocks the start.
 */
@Log4j2
public final class RunLogRetention {

    /**
     * Number of days of run logs kept, counting today.
     */
    public static final int RETENTION_DAYS = 30;

    private RunLogRetention() {
    }

    /**
     * Deletes day directories named {@code yyyy-MM-dd} that are older than the retention period.
     * Directories with other names and links are left untouched.
     *
     * @param installation
     *         canonical installation root
     * @param component
     *         {@code launcher} or {@code updater}
     * @param today
     *         current local date
     * @return number of deleted day directories
     * @throws NullPointerException
     *         if an argument is null
     */
    public static int deleteExpired( @Nonnull Path installation, @Nonnull String component, @Nonnull LocalDate today ) {
        requireNonNull( installation, "installation must not be null" );
        requireNonNull( component, "component must not be null" );
        requireNonNull( today, "today must not be null" );
        Path directory = installation.resolve( UpdateLayout.PANEL_DIRECTORY ).resolve( RunLogFile.LOGS_DIRECTORY )
                .resolve( component );
        if ( !Files.isDirectory( directory, NOFOLLOW_LINKS ) ) {
            return 0;
        }
        LocalDate oldestKept = today.minusDays( RETENTION_DAYS - 1L );
        int deleted = 0;
        try {
            FileWorldSnapshotStore.checkDirectory( installation, directory );
            try ( DirectoryStream< Path > days = Files.newDirectoryStream( directory ) ) {
                for ( Path day : days ) {
                    if ( isExpired( day, oldestKept ) ) {
                        DurableFiles.deleteTree( installation, day );
                        deleted++;
                    }
                }
            }
        } catch ( IOException | RuntimeException exception ) {
            log.warn( "Could not apply retention to {} run logs", component, exception );
        }
        log.debug( "Removed {} expired {} run log directories", deleted, component );
        return deleted;
    }

    private static boolean isExpired( Path day, LocalDate oldestKept ) {
        if ( !Files.isDirectory( day, NOFOLLOW_LINKS ) ) {
            return false;
        }
        try {
            return LocalDate.parse( day.getFileName().toString() ).isBefore( oldestKept );
        } catch ( DateTimeParseException exception ) {
            return false;
        }
    }
}
