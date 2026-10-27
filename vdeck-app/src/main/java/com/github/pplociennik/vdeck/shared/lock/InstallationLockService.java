package com.github.pplociennik.vdeck.shared.lock;

import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * The only place where the panel takes the installation lock over from the launcher (AD-024).
 *
 * <p>When the launcher starts the panel, it passes a handoff identifier in the {@code vdeck.handoff}
 * system property. The lock is then taken over <em>before</em> Spring starts, so no configuration or
 * database write can happen without it, registered as the {@value #BEAN_NAME} bean owned by the server
 * runtime and released again when the context fails to start. Without the property, as in IDE runs,
 * nothing is taken over and the runtime acquires the lock itself.
 *
 * <p>This is no Spring bean: it runs before the application context exists.
 */
@Log4j2
public final class InstallationLockService {

    /**
     * Name of the bean holding the taken-over lock, injected into the server runtime.
     */
    public static final String BEAN_NAME = "installationLock";

    private final Properties systemProperties;

    /**
     * Creates the service.
     *
     * @param systemProperties
     *         system properties carrying the handoff identifier and the installation directory
     * @throws NullPointerException
     *         if the properties are null
     */
    public InstallationLockService( @Nonnull Properties systemProperties ) {
        this.systemProperties = requireNonNull( systemProperties, "systemProperties must not be null" );
    }

    /**
     * Takes over the launcher's lock, if one is handed over, and runs the application with it.
     *
     * @param application
     *         application to run
     * @param args
     *         command line arguments passed to Spring Boot
     * @return the running application context
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalStateException
     *         if the handoff from the launcher is invalid or times out
     */
    public @Nonnull ConfigurableApplicationContext runWithLock( @Nonnull SpringApplication application,
                                                                @Nonnull String[] args ) {
        requireNonNull( application, "application must not be null" );
        requireNonNull( args, "args must not be null" );

        Optional< InstallationLock > handedOver = acceptHandoff();
        handedOver.ifPresent( lock -> application.addInitializers(
                context -> context.getBeanFactory().registerSingleton( BEAN_NAME, lock ) ) );

        try {
            return application.run( args );
        } catch ( RuntimeException exception ) {
            handedOver.ifPresent( InstallationLock::close );
            throw exception;
        }
    }

    private Optional< InstallationLock > acceptHandoff() {
        String operationId = systemProperties.getProperty( UpdateLayout.PANEL_HANDOFF_PROPERTY );
        if ( operationId == null ) {
            log.debug( "No launcher handoff; the runtime acquires the installation lock itself" );
            return Optional.empty();
        }

        String directory = systemProperties.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        if ( directory == null ) {
            throw new IllegalStateException( "A launcher handoff requires the installation directory" );
        }

        try {
            Path installation = Path.of( directory ).toRealPath();
            InstallationLock lock = new HandoffProtocol( installation )
                    .accept( operationId, HandoffRole.PANEL, HandoffProtocol.TIMEOUT ).lock();
            log.info( "Took the installation lock over from the launcher" );
            return Optional.of( lock );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "The installation directory cannot be resolved", exception );
        }
    }
}
