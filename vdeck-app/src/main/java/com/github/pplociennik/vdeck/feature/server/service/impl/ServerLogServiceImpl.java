package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.ServerLogService;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.stream.Stream;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readInt;
import static java.util.Objects.requireNonNull;

/**
 * Lays out the game server logs as one file per launch, grouped into a directory per day, and applies
 * the configured retention before each launch.
 */
@Log4j2
@Service
class ServerLogServiceImpl implements ServerLogService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd_HH-mm-ss" );

    private static final String LOG_SUFFIX = ".log";

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

    /**
     * Creates the service laying out game server logs.
     *
     * @param systemPropertiesService
     *         service reading {@code vDeck.properties}
     * @param pathService
     *         service resolving the configured directory against the installation directory
     */
    ServerLogServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                          @Nonnull PathService pathService ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    @Override
    public @Nonnull Path createLogFileForNewStart() {
        Path baseDirectory = resolveBaseDirectory();
        LocalDateTime now = LocalDateTime.now();

        applyRetention( baseDirectory, now.toLocalDate() );

        Path dayDirectory = baseDirectory.resolve( DAY_FORMAT.format( now ) );
        createDirectory( dayDirectory );

        Path logFile = availableLogFile( dayDirectory, FILE_FORMAT.format( now ) );
        log.info( "Game server log of this launch will be written to {}", logFile );
        return logFile;
    }

    private @Nonnull Path resolveBaseDirectory() {
        String configured = read( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_DIRECTORY );
        return pathService.getAbsolutePath( configured );
    }

    /**
     * Returns the first unused file name for this second. Two launches within one second are barely
     * possible, but appending an index is cheaper than losing a log to an overwrite.
     */
    private static @Nonnull Path availableLogFile( @Nonnull Path dayDirectory, @Nonnull String baseName ) {
        Path candidate = dayDirectory.resolve( baseName + LOG_SUFFIX );
        int index = 1;
        while ( Files.exists( candidate ) ) {
            candidate = dayDirectory.resolve( "%s-%d%s".formatted( baseName, index, LOG_SUFFIX ) );
            index++;
        }
        return candidate;
    }

    /**
     * Removes day directories older than the configured window. Never touches the current day, and
     * never lets a cleanup problem stop a launch: losing disk space is a smaller problem than refusing
     * to start the server.
     */
    private void applyRetention( @Nonnull Path baseDirectory, @Nonnull LocalDate today ) {
        if ( !Files.isDirectory( baseDirectory ) ) {
            return;
        }
        int retentionDays = readInt( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_DAYS, VdeckProperty.GAME_LOG_RETENTION_DAYS.getDefaultValue() );
        LocalDate oldestKept = today.minusDays( retentionDays );

        try ( Stream< Path > days = Files.list( baseDirectory ) ) {
            days.filter( Files::isDirectory )
                    .filter( day -> isExpired( day, oldestKept ) )
                    .forEach( ServerLogServiceImpl::deleteRecursively );
        } catch ( IOException exception ) {
            log.warn( "Could not apply game log retention in {}: {}", baseDirectory, exception.getMessage() );
        }
    }

    /**
     * Tells whether a directory is a day directory older than the retention window. A directory whose
     * name is not a date belongs to someone else and is left alone.
     */
    private static boolean isExpired( @Nonnull Path dayDirectory, @Nonnull LocalDate oldestKept ) {
        try {
            LocalDate day = LocalDate.parse( dayDirectory.getFileName().toString(), DAY_FORMAT );
            return day.isBefore( oldestKept );
        } catch ( DateTimeParseException exception ) {
            log.debug( "Ignoring {} during game log retention: not a day directory", dayDirectory );
            return false;
        }
    }

    private static void deleteRecursively( @Nonnull Path directory ) {
        try ( Stream< Path > entries = Files.walk( directory ) ) {
            entries.sorted( Comparator.reverseOrder() ).forEach( ServerLogServiceImpl::deleteQuietly );
            log.info( "Removed expired game log directory {}", directory );
        } catch ( IOException exception ) {
            log.warn( "Could not remove expired game log directory {}: {}", directory, exception.getMessage() );
        }
    }

    private static void deleteQuietly( @Nonnull Path entry ) {
        try {
            Files.deleteIfExists( entry );
        } catch ( IOException exception ) {
            log.warn( "Could not remove {}: {}", entry, exception.getMessage() );
        }
    }

    private static void createDirectory( @Nonnull Path directory ) {
        try {
            Files.createDirectories( directory );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to create game log directory " + directory, exception );
        }
    }
}