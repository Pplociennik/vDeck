package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.I18n;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * The first step of the panel start: the panel does not start in a directory without
 * {@code valheim_server.exe} (design §5), because it could not control any server there.
 *
 * <p>The installation directory is the {@code vdeck.server-directory} override or, without it, the
 * application home. The check is enabled by default; the system property {@value #ENABLED_PROPERTY}
 * set to {@code false} disables it, for example for IDE runs against a sandbox. Any other value keeps
 * it enabled.
 *
 * <p>This is no Spring bean: it runs before the application context exists. Logging is not configured
 * yet, so the user message is written straight to the given console stream.
 */
@Log4j2
public final class ServerExecutableCheck {

    /**
     * System property that disables the check when set to {@code false}.
     */
    public static final String ENABLED_PROPERTY = "vdeck.server-file-check";

    private final Properties systemProperties;

    private final Path applicationHome;

    private final PrintStream console;

    /**
     * Creates the check.
     *
     * @param systemProperties
     *         system properties carrying the switch and the installation directory override
     * @param applicationHome
     *         directory used when no installation directory override is set
     * @param console
     *         stream receiving the user message
     * @throws NullPointerException
     *         if an argument is null
     */
    public ServerExecutableCheck( @Nonnull Properties systemProperties, @Nonnull Path applicationHome,
                                  @Nonnull PrintStream console ) {
        this.systemProperties = requireNonNull( systemProperties, "systemProperties must not be null" );
        this.applicationHome = requireNonNull( applicationHome, "applicationHome must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
    }

    /**
     * Checks that {@code valheim_server.exe} is a regular file in the installation directory, telling the
     * user what is wrong when it is not.
     *
     * @return {@code true} when the file exists or the check is disabled, {@code false} when the panel
     *         must not start
     * @throws InvalidPathException
     *         if the installation directory override is not a valid path
     */
    public boolean passes() {
        if ( "false".equalsIgnoreCase( systemProperties.getProperty( ENABLED_PROPERTY, "" ).strip() ) ) {
            log.info( "The server executable check is disabled by {}", ENABLED_PROPERTY );
            return true;
        }

        Path directory = installationDirectory();
        if ( Files.isRegularFile( directory.resolve( ServerService.EXECUTABLE_NAME ) ) ) {
            log.debug( "Found {} in {}", ServerService.EXECUTABLE_NAME, directory );
            return true;
        }

        log.warn( "{} is missing in {}; the panel does not start", ServerService.EXECUTABLE_NAME, directory );
        console.println( I18n.text( ConsoleMessage.SERVER_EXECUTABLE_MISSING, directory ) );
        return false;
    }

    private Path installationDirectory() {
        String configured = systemProperties.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, "" ).strip();
        return configured.isEmpty() ? applicationHome : Path.of( configured );
    }
}
