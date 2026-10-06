package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Refuses a server start that would fail anyway, before any process is created.
 *
 * <p>Valheim reports most of these problems by writing one line to its log and exiting, which looks to
 * an administrator like the server silently refusing to come up. Checking first turns each of them
 * into a specific sentence on the screen.
 *
 * <p>Three of the rules are the server's own and are mirrored here deliberately: a password of at
 * least five characters, a password that does not occur inside the server name, and a public server
 * that has a password at all.
 */
@Log4j2
@Component
class ServerStartValidator {

    /**
     * Shortest password the Valheim server accepts.
     */
    static final int MINIMUM_PASSWORD_LENGTH = 5;

    private final PathService pathService;

    /**
     * Creates pre-start validation using stored secrets and confined installation paths.
     *
     * @param pathService
     *         resolver restricting paths to the installation
     * @throws NullPointerException
     *         if the path service is null
     */
    ServerStartValidator( @Nonnull PathService pathService ) {
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    /**
     * Runs every check against the stored profile. The only side effect is creating the world
     * directory, which the server needs to exist before it starts.
     *
     * @param configuration
     *         complete configuration captured for this start
     * @param executable
     *         expected location of {@code valheim_server.exe}
     * @throws NullPointerException
     *         if any argument is {@code null}
     * @throws ServerStartException
     *         if any check refuses the launch
     */
    void validate( @Nonnull LaunchConfiguration configuration, @Nonnull Path executable ) {
        requireNonNull( configuration, "configuration must not be null" );
        LaunchProfile profile = configuration.profile();
        requireNonNull( executable, "executable must not be null" );

        requireExecutable( executable );
        requireCompleteProfile( profile );
        requireUsablePassword( profile, configuration.password() );
        requireFreePorts( profile.port() );
        requireWritableSaveDirectory( profile.saveDirectory() );
        log.debug( "Pre-start checks passed for server '{}'", profile.serverName() );
    }

    private static void requireExecutable( @Nonnull Path executable ) {
        if ( !Files.isRegularFile( executable ) ) {
            log.debug( "Start rejected: {} is not a regular file", executable );
            throw ServerStartException.rejected( ServerMessage.EXECUTABLE_MISSING );
        }
    }

    private static void requireCompleteProfile( @Nonnull LaunchProfile profile ) {
        boolean complete = !profile.serverName().isBlank()
                && !profile.worldName().isBlank()
                && !profile.saveDirectory().isBlank();
        if ( !complete ) {
            log.debug( "Start rejected: the launch profile is missing a mandatory setting" );
            throw ServerStartException.rejected( ServerMessage.PROFILE_INCOMPLETE );
        }
    }

    /** Checks the same captured password that will be passed to the process. */
    private static void requireUsablePassword( @Nonnull LaunchProfile profile, @Nonnull String password ) {
        if ( password.isEmpty() ) {
            requirePrivateServer( profile );
            return;
        }
        if ( password.length() < MINIMUM_PASSWORD_LENGTH ) {
            log.debug( "Start rejected: the server password is shorter than {} characters",
                    MINIMUM_PASSWORD_LENGTH );
            throw ServerStartException.rejected( ServerMessage.PASSWORD_TOO_SHORT );
        }
        if ( LaunchProfile.isPasswordPartOfName( password, profile.serverName() ) ) {
            log.debug( "Start rejected: the server password occurs inside the server name" );
            throw ServerStartException.rejected( ServerMessage.PASSWORD_PART_OF_NAME );
        }
    }

    private static void requirePrivateServer( @Nonnull LaunchProfile profile ) {
        if ( profile.publicServer() ) {
            log.debug( "Start rejected: a public server needs a password" );
            throw ServerStartException.rejected( ServerMessage.PUBLIC_REQUIRES_PASSWORD );
        }
    }

    /**
     * Checks both ports the server binds. Valheim uses the configured port and the one above it, so
     * checking only the first would let the server start and then fail to become reachable.
     */
    private static void requireFreePorts( int port ) {
        for ( int candidate = port; candidate <= port + 1; candidate++ ) {
            requireFreePort( candidate );
        }
    }

    private static void requireFreePort( int port ) {
        try ( DatagramSocket probe = new DatagramSocket( port ) ) {
            log.debug( "UDP port {} is free", probe.getLocalPort() );
        } catch ( SocketException exception ) {
            log.debug( "Start rejected: UDP port {} is already in use", port );
            throw ServerStartException.rejected( ServerMessage.PORT_IN_USE );
        }
    }

    /**
     * Makes sure the world directory exists and can be written to, because the server creates the world
     * there on its first run and would otherwise lose it.
     */
    private void requireWritableSaveDirectory( @Nonnull String configuredDirectory ) {
        Path saveDirectory = prepareSaveDirectory( configuredDirectory );
        if ( !Files.isWritable( saveDirectory ) ) {
            log.debug( "Start rejected: world directory {} is not writable", saveDirectory );
            throw ServerStartException.rejected( ServerMessage.SAVE_DIRECTORY_NOT_WRITABLE );
        }
    }

    /**
     * Resolves the configured world directory against the installation directory and creates it. Every
     * way this can go wrong means the same thing to the administrator, so they share one rejection.
     */
    private @Nonnull Path prepareSaveDirectory( @Nonnull String configuredDirectory ) {
        try {
            Path saveDirectory = pathService.getAbsolutePath( configuredDirectory );
            Files.createDirectories( saveDirectory );
            return saveDirectory;
        } catch ( PathResolutionException | IllegalArgumentException | IOException
                  | UncheckedIOException exception ) {
            log.debug( "Start rejected: world directory '{}' is unusable: {}", configuredDirectory,
                    exception.getMessage() );
            throw ServerStartException.rejected( ServerMessage.SAVE_DIRECTORY_NOT_WRITABLE );
        }
    }
}
