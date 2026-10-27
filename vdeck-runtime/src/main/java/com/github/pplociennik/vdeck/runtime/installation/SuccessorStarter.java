package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Starts a successor process in the shared console and hands the installation lock over to it through
 * {@link HandoffProtocol} (AD-024).
 *
 * <p>When no valid acknowledgement arrives in time, the successor — always a process this class started
 * itself — is terminated and its exit observed before the caller regains the lock. Valheim and foreign
 * processes are never terminated. If the successor cannot be stopped, the lock is not regained and the
 * caller must stop without touching the installation.
 */
@Log4j2
public final class SuccessorStarter {

    private final Path installation;

    private final HandoffProtocol protocol;

    private final ProcessFactory processes;

    private final Duration timeout;

    /**
     * Creates a starter for the current process.
     *
     * @param installation
     *         canonical installation root, also the successor's working directory
     * @throws NullPointerException
     *         if the installation is null
     */
    public SuccessorStarter( @Nonnull Path installation ) {
        this( installation, new HandoffProtocol( installation ),
                ( command, directory ) -> new ProcessBuilder( command ).directory( directory.toFile() ).inheritIO()
                        .start(), HandoffProtocol.TIMEOUT );
    }

    /**
     * Creates a starter with explicit boundaries.
     *
     * @param installation
     *         canonical installation root
     * @param protocol
     *         handoff protocol of the installation
     * @param processes
     *         creates successor processes
     * @param timeout
     *         bound of the acknowledgement wait and of each termination wait
     */
    SuccessorStarter( @Nonnull Path installation, @Nonnull HandoffProtocol protocol,
                      @Nonnull ProcessFactory processes, @Nonnull Duration timeout ) {
        this.installation = requireNonNull( installation, "installation must not be null" );
        this.protocol = requireNonNull( protocol, "protocol must not be null" );
        this.processes = requireNonNull( processes, "processes must not be null" );
        this.timeout = requireNonNull( timeout, "timeout must not be null" );
    }

    /**
     * Starts the successor and hands the installation lock over. The caller keeps its startup gate.
     *
     * <pre>
     * start --handoff id --> publish descriptor --> release lock --> await acknowledgement
     *   | start failed          | publish failed                      | missing / invalid
     *   v                       v                                     v
     * Failed(lock kept)     terminate, Failed(lock kept)      terminate, observe exit, regain lock
     * </pre>
     *
     * @param lock
     *         held installation lock; released only after the descriptor is published
     * @param role
     *         role of the successor
     * @param command
     *         builds the successor command line from the handoff identifier
     * @return accepted successor, or failure with the lock state
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the lock is not a held installation lock
     */
    public @Nonnull HandoffResult handOff( @Nonnull InstallationLock lock, @Nonnull HandoffRole role,
                                           @Nonnull Function< String, List< String > > command ) {
        requireNonNull( lock, "lock must not be null" );
        requireNonNull( role, "role must not be null" );
        requireNonNull( command, "command must not be null" );
        if ( lock.getKind() != InstallationLock.Kind.INSTALLATION || !lock.isHeld() ) {
            throw new IllegalArgumentException( "lock must be a held installation lock" );
        }
        String operationId = HandoffProtocol.newOperationId();
        Process successor;
        try {
            successor = processes.start( List.copyOf( command.apply( operationId ) ), installation );
        } catch ( IOException | RuntimeException exception ) {
            log.error( "Could not start the {} successor", role, exception );
            return new HandoffResult.Failed( Optional.of( lock ) );
        }
        log.info( "Started {} successor process {} for handoff {}", role, successor.pid(), operationId );
        var identity = publish( operationId, role, successor );
        if ( identity.isEmpty() ) {
            return terminate( successor ) ? new HandoffResult.Failed( Optional.of( lock ) )
                    : new HandoffResult.Failed( Optional.empty() );
        }
        lock.close();
        if ( protocol.awaitAcknowledgement( operationId, identity.get(), timeout ) ) {
            return new HandoffResult.Accepted( successor );
        }
        if ( !terminate( successor ) ) {
            log.error( "The {} successor could not be stopped; the installation stays untouched", role );
            return new HandoffResult.Failed( Optional.empty() );
        }
        return new HandoffResult.Failed( InstallationLock.tryAcquire( installation, InstallationLock.Kind.INSTALLATION ) );
    }

    private Optional< ProcessIdentity > publish( String operationId, HandoffRole role, Process successor ) {
        try {
            return Optional.of( protocol.publish( operationId, role, successor.toHandle() ) );
        } catch ( RuntimeException exception ) {
            log.error( "Could not publish handoff {}", operationId, exception );
            return Optional.empty();
        }
    }

    private boolean terminate( Process successor ) {
        try {
            if ( !successor.isAlive() ) {
                return true;
            }
            log.warn( "Terminating successor process {} after a failed handoff", successor.pid() );
            successor.destroy();
            if ( successor.waitFor( timeout.toNanos(), TimeUnit.NANOSECONDS ) ) {
                return true;
            }
            successor.destroyForcibly();
            return successor.waitFor( timeout.toNanos(), TimeUnit.NANOSECONDS );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Creates successor processes.
     */
    @FunctionalInterface
    interface ProcessFactory {

        /**
         * Starts a process.
         *
         * @param command
         *         executable and arguments as separate values
         * @param workingDirectory
         *         working directory of the process
         * @return started process
         * @throws IOException
         *         if the process cannot be started
         */
        Process start( List< String > command, Path workingDirectory ) throws IOException;
    }
}
