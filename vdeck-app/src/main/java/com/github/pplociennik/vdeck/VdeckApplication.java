package com.github.pplociennik.vdeck;

import com.github.pplociennik.vdeck.feature.server.ServerExecutableCheck;
import com.github.pplociennik.vdeck.shared.lock.InstallationLockService;
import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;

import static java.util.Objects.requireNonNull;

/**
 * Entry point of the vDeck web panel managing a local Valheim dedicated server.
 *
 * <p>{@link ServerExecutableCheck} first stops the start, with exit code 1, in a directory without
 * {@code valheim_server.exe}. {@link InstallationLockService} then takes the installation lock over from
 * the launcher before Spring starts (AD-024).
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
        if ( !new ServerExecutableCheck( System.getProperties(),
                new ApplicationHome( VdeckApplication.class ).getDir().toPath(), System.out ).passes() ) {
            System.exit( 1 );
        }
        new InstallationLockService( System.getProperties() )
                .runWithLock( new SpringApplication( VdeckApplication.class ), args );
    }
}
