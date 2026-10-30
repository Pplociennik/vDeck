package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.I18n;
import com.github.pplociennik.vdeck.shared.InstallationDirectory;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * The first step of the panel start: the panel does not start in a directory without
 * {@code valheim_server.exe} (design §5), because it could not control any server there.
 *
 * <p>The installation directory is the {@code vdeck.server-directory} override or, without it, the
 * application home. The check is enabled by default; it is disabled by the setting
 * {@code server.file.check=false} in {@code .vDeck/config/vDeck.properties} or by the system property
 * {@value #ENABLED_PROPERTY} set to {@code false}, for example for IDE runs against a sandbox. Any other
 * value keeps it enabled.
 *
 * <p>This is no Spring bean: it runs before the application context exists, so it reads
 * {@code vDeck.properties} straight from the file instead of through {@code SystemPropertiesService}. A
 * missing or unreadable file keeps the check enabled. Logging is not configured yet, so the user message
 * is written straight to the given console stream.
 */
@Log4j2
public final class ServerExecutableCheck {

    /**
     * System property that disables the check when set to {@code false}.
     */
    public static final String ENABLED_PROPERTY = "vdeck.server-file-check";

    private static final int MAX_SETTINGS_BYTES = 64 * 1024;

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
        if ( isFalse( systemProperties.getProperty( ENABLED_PROPERTY ) ) ) {
            log.info( "The server executable check is disabled by {}", ENABLED_PROPERTY );
            return true;
        }

        Path directory = InstallationDirectory.resolve( systemProperties, applicationHome );
        if ( isFalse( readSetting( directory ) ) ) {
            log.info( "The server executable check is disabled by {} in {}", VdeckProperty.SERVER_FILE_CHECK.getName(),
                    VdeckProperty.FILE_NAME );
            return true;
        }

        if ( Files.isRegularFile( directory.resolve( ServerService.EXECUTABLE_NAME ) ) ) {
            log.debug( "Found {} in {}", ServerService.EXECUTABLE_NAME, directory );
            return true;
        }

        log.warn( "{} is missing in {}; the panel does not start", ServerService.EXECUTABLE_NAME, directory );
        console.println( I18n.text( ConsoleMessage.SERVER_EXECUTABLE_MISSING, directory ) );
        return false;
    }

    private static @Nullable String readSetting( @Nonnull Path directory ) {
        Path file = directory.resolve( DistributionItem.PANEL_SETTINGS.getPath() );
        if ( !Files.exists( file, NOFOLLOW_LINKS ) ) {
            return null;
        }
        try {
            return DurableFiles.readProperties( file, MAX_SETTINGS_BYTES )
                    .getProperty( VdeckProperty.SERVER_FILE_CHECK.getName() );
        } catch ( IOException exception ) {
            // Documented fallback: an unreadable settings file keeps the check enabled.
            log.warn( "Failed to read {}; the server executable check stays enabled", file, exception );
            return null;
        }
    }

    private static boolean isFalse( @Nullable String value ) {
        return value != null && "false".equalsIgnoreCase( value.strip() );
    }
}
