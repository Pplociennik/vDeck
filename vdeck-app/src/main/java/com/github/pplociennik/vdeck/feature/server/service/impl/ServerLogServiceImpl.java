package com.github.pplociennik.vdeck.feature.server.service.impl;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readBoolean;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readInt;

import com.github.pplociennik.vdeck.feature.server.service.ServerLogService;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static java.util.Objects.requireNonNull;

/**
 * Lays out the game server logs as one file per launch, grouped into a directory per day, and applies
 * the configured retention before each launch. Directory actions are delegated to
 * {@link ServerLogDirectories} and the removal of old days to {@link ServerLogRetention}; this service reads
 * the panel settings that drive them.
 */
@Log4j2
@Service
class ServerLogServiceImpl implements ServerLogService {

    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd_HH-mm-ss" );

    private static final String LOG_SUFFIX = ".log";

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

    private final Clock clock;

    /**
     * Creates the service laying out game server logs.
     *
     * @param systemPropertiesService
     *         service reading {@code vDeck.properties}
     * @param pathService
     *         service resolving the configured directory against the installation directory
     */
    @Autowired
    ServerLogServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                          @Nonnull PathService pathService ) {
        this( systemPropertiesService, pathService, Clock.systemDefaultZone() );
    }

    /**
     * Creates log storage with an explicit clock for day boundaries and file collisions.
     *
     * @param systemPropertiesService
     *         panel settings
     * @param pathService
     *         confined path resolver
     * @param clock
     *         clock and zone used for log dates
     * @throws NullPointerException
     *         if any dependency is null
     */
    ServerLogServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                          @Nonnull PathService pathService, @Nonnull Clock clock ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
        this.clock = requireNonNull( clock, "clock must not be null" );
    }

    @Override
    public @Nonnull Path createLogFileForNewStart() {
        Path baseDirectory = resolveBaseDirectory();
        LocalDateTime now = LocalDateTime.now( clock );

        applyRetention( baseDirectory, now.toLocalDate() );

        Path dayDirectory = ServerLogDirectories.dayDirectory( baseDirectory, now.toLocalDate() );
        ServerLogDirectories.createIfMissing( dayDirectory );

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
     * Removes day directories older than the configured window, unless retention is switched off. On a
     * first launch the log directory does not exist yet, so there is nothing to clean up and the settings
     * are not read.
     */
    private void applyRetention( @Nonnull Path baseDirectory, @Nonnull LocalDate today ) {
        if ( !Files.isDirectory( baseDirectory ) ) {
            return;
        }
        if ( !isRetentionEnabled() ) {
            log.debug( "Game log retention is disabled, keeping every log in {}", baseDirectory );
            return;
        }

        LocalDate oldestKept = today.minusDays( retentionDays() );
        ServerLogRetention.removeDaysBefore( baseDirectory, oldestKept );
    }

    private boolean isRetentionEnabled() {
        return readBoolean( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_ENABLED, VdeckProperty.GAME_LOG_RETENTION_ENABLED.getDefaultValue() );
    }

    /**
     * Returns the configured retention window in days. A negative window would remove today's logs, so it
     * is replaced by the default, as is a value that is not a number.
     */
    private int retentionDays() {
        String defaultDays = VdeckProperty.GAME_LOG_RETENTION_DAYS.getDefaultValue();
        int retentionDays = readInt( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_DAYS, defaultDays );
        if ( retentionDays < 0 ) {
            log.warn( "Negative game log retention, using the default retention window" );
            return Integer.parseInt( defaultDays );
        }
        return retentionDays;
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
}
