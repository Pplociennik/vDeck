package com.github.pplociennik.vdeck;

import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Entry point of the vDeck web panel managing a local Valheim dedicated server.
 *
 * <p>When the launcher starts the panel, it passes a handoff identifier in the {@code vdeck.handoff}
 * system property. The panel then takes the installation lock over <em>before</em> Spring starts, so no
 * configuration or database write can happen without it, and registers the lock as the
 * {@code installationLock} bean owned by the server runtime (AD-024). Without the property, as in IDE runs,
 * the runtime acquires the lock itself.
 */
@SpringBootApplication
public class VdeckApplication {

    private static final String INSTALLATION_LOCK_BEAN = "installationLock";

    /**
     * Takes over the installation lock when started by the launcher and starts the panel's Spring
     * context. The lock is released when the context fails to start.
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
        Optional< InstallationLock > handedOver = acceptHandoff();
        SpringApplication application = new SpringApplication( VdeckApplication.class );
        handedOver.ifPresent( lock -> application.addInitializers(
                context -> context.getBeanFactory().registerSingleton( INSTALLATION_LOCK_BEAN, lock ) ) );
        try {
            application.run( args );
        } catch ( RuntimeException exception ) {
            handedOver.ifPresent( InstallationLock::close );
            throw exception;
        }
    }

    private static Optional< InstallationLock > acceptHandoff() {
        String operationId = System.getProperty( UpdateLayout.PANEL_HANDOFF_PROPERTY );
        if ( operationId == null ) {
            return Optional.empty();
        }
        String directory = System.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        if ( directory == null ) {
            throw new IllegalStateException( "A launcher handoff requires the installation directory" );
        }
        try {
            Path installation = Path.of( directory ).toRealPath();
            return Optional.of( new HandoffProtocol( installation )
                    .accept( operationId, HandoffRole.PANEL, HandoffProtocol.TIMEOUT ).lock() );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "The installation directory cannot be resolved", exception );
        }
    }
}
