package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.service.LaunchCommandService;
import com.github.pplociennik.vdeck.feature.server.service.ServerLogService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Carries out the server start: checks first, then prepares this launch's log file and command line,
 * and finally hands the launch to the runtime.
 *
 * <p>This class holds no state of its own. The lifecycle state lives in the runtime and is read from
 * it every time, because a copy kept here would go stale the moment the server exited on its own.
 */
@Log4j2
@Service
class ServerServiceImpl implements ServerService {

    private final ServerRuntime serverRuntime;

    private final LaunchProfileService launchProfileService;

    private final LaunchCommandService launchCommandService;

    private final ServerLogService serverLogService;

    private final ServerStartValidator validator;

    private final PathService pathService;

    /**
     * Creates the service controlling the Valheim server.
     *
     * @param serverRuntime
     *         runtime owning the managed process and its state
     * @param launchProfileService
     *         service reading the stored launch profile
     * @param launchCommandService
     *         service turning the profile into a command line and its mirror script
     * @param serverLogService
     *         service deciding where this launch writes its log
     * @param validator
     *         pre-start checks refusing a launch that would fail anyway
     * @param pathService
     *         service resolving the executable and the mirror script inside the installation
     */
    ServerServiceImpl( @Nonnull ServerRuntime serverRuntime,
                       @Nonnull LaunchProfileService launchProfileService,
                       @Nonnull LaunchCommandService launchCommandService,
                       @Nonnull ServerLogService serverLogService,
                       @Nonnull ServerStartValidator validator,
                       @Nonnull PathService pathService ) {
        this.serverRuntime = requireNonNull( serverRuntime, "serverRuntime must not be null" );
        this.launchProfileService = requireNonNull( launchProfileService,
                "launchProfileService must not be null" );
        this.launchCommandService = requireNonNull( launchCommandService,
                "launchCommandService must not be null" );
        this.serverLogService = requireNonNull( serverLogService, "serverLogService must not be null" );
        this.validator = requireNonNull( validator, "validator must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    @Override
    public void start() {
        log.info( "Server start requested" );
        requireStartableState();

        LaunchConfiguration configuration = launchProfileService.getConfiguration();
        validator.validate( configuration, executable() );

        LaunchSpec launchSpec = launchCommandService.build( configuration, reserveLogFile() );
        writeStartScript( launchCommandService.renderScript( launchSpec ) );
        launch( launchSpec );
    }

    @Override
    public @Nonnull ServerStatus getStatus() {
        return serverRuntime.status();
    }

    /**
     * Refuses early when a server is already being managed. The runtime checks this again on its own
     * thread, which is what actually settles a race between two requests; this check exists so the
     * common case is refused before any file is touched.
     */
    private void requireStartableState() {
        if ( serverRuntime.status().getState().isStartable() ) {
            return;
        }
        log.info( "Server start rejected: the server is {}", serverRuntime.status().getState() );
        throw ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
    }

    private @Nonnull Path executable() {
        return pathService.getBaseDirectory().resolve( EXECUTABLE_NAME );
    }

    private @Nonnull Path reserveLogFile() {
        try {
            return serverLogService.createLogFileForNewStart();
        } catch ( UncheckedIOException exception ) {
            log.debug( "Server start rejected: log directory unusable: {}", exception.getMessage() );
            throw ServerStartException.rejected( ServerMessage.LOG_DIRECTORY_NOT_WRITABLE );
        }
    }

    /**
     * Hands the launch to the runtime. An {@link IllegalStateException} means the runtime settled a
     * race and another start won it; anything else means the process could not be created.
     */
    private void launch( @Nonnull LaunchSpec launchSpec ) {
        try {
            ServerStatus status = serverRuntime.start( launchSpec );
            log.info( "Server start accepted, state {}", status.getState() );
        } catch ( IllegalStateException exception ) {
            log.info( "Server start rejected: another start won the race" );
            throw ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
        } catch ( RuntimeException exception ) {
            throw ServerStartException.startFailed( exception );
        }
    }

    /**
     * Writes the mirror script next to the panel's own files.
     *
     * <p>A failure here is deliberately not fatal: the script is a convenience for a human reading it
     * later, never a condition for starting the server. It is written atomically so a crash cannot
     * leave a half-written file that looks like a valid command.
     */
    private void writeStartScript( @Nonnull String content ) {
        Path script = pathService.getAbsolutePath( START_SCRIPT_NAME );
        Path temporaryFile = null;
        try {
            Files.createDirectories( script.getParent() );
            temporaryFile = Files.createTempFile( script.getParent(), "vDeck", ".bat.tmp" );
            Files.writeString( temporaryFile, content, StandardCharsets.US_ASCII );
            Files.move( temporaryFile, script, REPLACE_EXISTING, ATOMIC_MOVE );
            temporaryFile = null;
            log.info( "Wrote the start script mirroring this launch to {}", script );
        } catch ( IOException exception ) {
            log.warn( "Could not write the start script {}: {}", script, exception.getMessage() );
        } finally {
            deleteIfPresent( temporaryFile );
        }
    }

    private static void deleteIfPresent( @Nullable Path temporaryFile ) {
        if ( temporaryFile == null ) {
            return;
        }
        try {
            Files.deleteIfExists( temporaryFile );
        } catch ( IOException exception ) {
            log.warn( "Could not delete temporary start script {}", temporaryFile );
        }
    }
}
