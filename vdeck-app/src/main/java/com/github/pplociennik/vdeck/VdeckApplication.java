package com.github.pplociennik.vdeck;

import com.github.pplociennik.vdeck.feature.server.ServerExecutableCheck;
import com.github.pplociennik.vdeck.shared.InstallationDirectory;
import com.github.pplociennik.vdeck.shared.lock.InstallationLockService;
import com.github.pplociennik.vdeck.shared.log.PanelRunLog;
import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * Entry point of the vDeck web panel managing a local Valheim dedicated server.
 *
 * <p>{@link ServerExecutableCheck} first stops the start, with exit code 1, in a directory without
 * {@code valheim_server.exe}. {@link PanelRunLog} then creates the log file of this run (AD-032), and
 * {@link InstallationLockService} takes the installation lock over from the launcher before Spring starts
 * (AD-024).
 */
@SpringBootApplication
public class VdeckApplication {

    /**
     * Checks for {@code valheim_server.exe} and starts the panel's Spring context with the installation
     * lock handed over by the launcher, if any.
     *
     * @param args
     *         command line arguments passed to Spring Boot
     * @throws NullPointerException
     *         if args is null
     * @throws IllegalStateException
     *         if the handoff from the launcher is invalid or times out
     */
    public static void main( @Nonnull String[] args ) {
        requireNonNull( args, "args must not be null" );
        Properties systemProperties = System.getProperties();
        Path applicationHome = new ApplicationHome( VdeckApplication.class ).getDir().toPath();
        if ( !new ServerExecutableCheck( systemProperties, applicationHome, System.out ).passes() ) {
            System.exit( 1 );
        }

        PanelRunLog.configure( systemProperties, InstallationDirectory.resolve( systemProperties, applicationHome ),
                LocalDateTime.now() );
        new InstallationLockService( systemProperties )
                .runWithLock( new SpringApplication( VdeckApplication.class ), args );
    }
}
