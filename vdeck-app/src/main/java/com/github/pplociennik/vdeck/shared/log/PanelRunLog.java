package com.github.pplociennik.vdeck.shared.log;

import com.github.pplociennik.vdeck.runtime.installation.RunLogFile;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * Creates the log file of one panel run, {@code .vDeck/logs/panel/<yyyy-MM-dd>/<yyyy-MM-dd_HH-mm-ss>.log}
 * (AD-032), publishes its path as {@link RunLogFile#LOG_FILE_PROPERTY} and adds {@link #FILE_CONFIGURATION}
 * to the Log4j2 overrides Spring Boot applies while it starts. Without a file, as in tests, the overrides
 * from {@code application.yaml} stay unchanged and the panel logs to the console alone.
 *
 * <p>Runs in {@code VdeckApplication.main()} before Spring configures logging, so it deliberately has no
 * logger. A file that cannot be created only leaves the panel without a file log; the start goes on.
 */
public final class PanelRunLog {

    /**
     * Run log component of the panel, the directory below {@code .vDeck/logs}.
     */
    public static final String COMPONENT = "panel";

    /**
     * Spring Boot property listing the Log4j2 configurations merged into its default one. A system property
     * replaces the list from {@code application.yaml}, so {@link #OVERRIDES} repeats the console override.
     */
    static final String OVERRIDE_PROPERTY = "logging.log4j2.config.override";

    /**
     * Log4j2 override adding the file appender of this run to the root logger.
     */
    static final String FILE_CONFIGURATION = "classpath:log4j2-vdeck-file.xml";

    /**
     * Overrides of a run with a log file: the console override of {@code application.yaml}, which must stay
     * equal to it, followed by {@link #FILE_CONFIGURATION}.
     */
    static final String OVERRIDES = "classpath:log4j2-vdeck-console.xml," + FILE_CONFIGURATION;

    private PanelRunLog() {
    }

    /**
     * Creates the log file of this run and publishes its path and the file override in the given system
     * properties.
     *
     * @param systemProperties
     *         system properties read by the Log4j2 configuration
     * @param installation
     *         installation directory
     * @param now
     *         local start time of the run
     * @return created file, or an empty result when no file could be created safely
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull Optional< Path > configure( @Nonnull Properties systemProperties,
                                                       @Nonnull Path installation, @Nonnull LocalDateTime now ) {
        requireNonNull( systemProperties, "systemProperties must not be null" );
        requireNonNull( installation, "installation must not be null" );
        requireNonNull( now, "now must not be null" );

        Optional< Path > file = RunLogFile.create( installation, COMPONENT, now );
        file.ifPresent( path -> {
            systemProperties.setProperty( RunLogFile.LOG_FILE_PROPERTY, path.toString() );
            systemProperties.setProperty( OVERRIDE_PROPERTY, OVERRIDES );
        } );
        return file;
    }
}
