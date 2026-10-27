package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Creates the log file of one launcher or updater run:
 * {@code .vDeck/logs/<component>/<yyyy-MM-dd>/<yyyy-MM-dd_HH-mm-ss>[-n].log} (AD-024).
 *
 * <p>This class is used by bootstrap code before Log4j2 is configured, so it deliberately has no logger
 * and touches no class that has one. Failures are reported as an empty result; the caller then logs to
 * the console only and the start continues.
 */
public final class RunLogFile {

    /**
     * Directory of run logs below {@code .vDeck}.
     */
    public static final String LOGS_DIRECTORY = "logs";

    /**
     * System property read by the Log4j2 configuration of the launcher and updater.
     */
    public static final String LOG_FILE_PROPERTY = "vdeck.log.file";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern( "yyyy-MM-dd_HH-mm-ss" );

    private static final int MAX_COLLISIONS = 100;

    private RunLogFile() {
    }

    /**
     * Creates a new, empty log file for this run, adding a numeric suffix when a file of the same second
     * already exists. No directory on the way may be a link or leave the installation.
     *
     * @param installation
     *         absolute installation root
     * @param component
     *         {@code launcher} or {@code updater}
     * @param now
     *         local start time of the run
     * @return created file, or an empty optional when no file could be created safely
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull Optional< Path > create( @Nonnull Path installation, @Nonnull String component,
                                                   @Nonnull LocalDateTime now ) {
        requireNonNull( installation, "installation must not be null" );
        requireNonNull( component, "component must not be null" );
        requireNonNull( now, "now must not be null" );
        try {
            Path root = installation.toRealPath();
            Path directory = root.resolve( UpdateLayout.PANEL_DIRECTORY ).resolve( LOGS_DIRECTORY )
                    .resolve( component ).resolve( DAY.format( now ) );
            Files.createDirectories( directory );
            if ( !isContainedDirectory( root, directory ) ) {
                return Optional.empty();
            }
            String name = TIMESTAMP.format( now );
            for ( int collision = 0; collision < MAX_COLLISIONS; collision++ ) {
                String suffix = collision == 0 ? "" : "-" + collision;
                try {
                    return Optional.of( Files.createFile( directory.resolve( name + suffix + ".log" ) ) );
                } catch ( FileAlreadyExistsException exception ) {
                    // Another run started in the same second; try the next suffix.
                }
            }
            return Optional.empty();
        } catch ( IOException | RuntimeException exception ) {
            return Optional.empty();
        }
    }

    private static boolean isContainedDirectory( Path root, Path directory ) throws IOException {
        for ( Path part = directory; !part.equals( root ); part = part.getParent() ) {
            if ( Files.isSymbolicLink( part ) || !Files.isDirectory( part, NOFOLLOW_LINKS )
                    || !part.toRealPath().equals( part ) ) {
                return false;
            }
        }
        return true;
    }
}
