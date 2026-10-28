package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelRole;
import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/**
 * Creates mandatory seed data after the application context has started.
 * Existing records are never overwritten, making repeated application starts idempotent.
 */
@Component
@Log4j2
class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ReleaseNotesService releaseNotesService;

    /**
     * Creates the initializer using the panel-user store, configured password encoder and release notes
     * service.
     *
     * @param userRepository
     *         repository used to find and create the default account
     * @param passwordEncoder
     *         encoder used before the initial password is persisted
     * @param releaseNotesService
     *         service marking the installed version's change list as seen on a fresh installation
     */
    DataInitializer( @Nonnull UserRepository userRepository,
                     @Nonnull PasswordEncoder passwordEncoder,
                     @Nonnull ReleaseNotesService releaseNotesService ) {
        this.userRepository = requireNonNull( userRepository, "userRepository must not be null" );
        this.passwordEncoder = requireNonNull( passwordEncoder, "passwordEncoder must not be null" );
        this.releaseNotesService = requireNonNull( releaseNotesService, "releaseNotesService must not be null" );
    }

    /**
     * Creates the default administrator when no account with its username exists.
     * A missing administrator means a fresh database, so the installed version's change list is marked as
     * seen at the same time: a fresh installation has nothing new to announce, only an update does.
     * A persistence failure is propagated so the application cannot start without usable credentials.
     *
     * @param args
     *         application arguments supplied by Spring Boot; not interpreted by this initializer
     */
    @Override
    @Transactional
    public void run( @Nonnull String... args ) {
        requireNonNull( args, "args must not be null" );
        if ( userRepository.existsById( DEFAULT_ADMIN_USERNAME ) ) {
            log.debug( "Default panel administrator already exists" );
            return;
        }

        String passwordHash = passwordEncoder.encode( DEFAULT_ADMIN_PASSWORD );
        PanelUser defaultAdmin = new PanelUser( DEFAULT_ADMIN_USERNAME, passwordHash, PanelRole.ADMIN, true );
        userRepository.save( defaultAdmin );
        log.info( "Created the default panel administrator; password change is required" );
        releaseNotesService.acknowledgeInstalled();
    }
}
