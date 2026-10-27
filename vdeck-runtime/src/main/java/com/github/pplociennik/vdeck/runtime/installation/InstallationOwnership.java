package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Both coordination locks held by a launcher or updater (AD-024). Only an owner of both may recover,
 * clean up, change settings or replace files. Closing releases both; releasing the installation lock
 * earlier through a handoff is allowed and closing it again is harmless.
 *
 * @param gate
 *         held startup gate
 * @param lock
 *         held installation lock, possibly already handed over
 */
@Log4j2
public record InstallationOwnership( @Nonnull InstallationLock gate, @Nonnull InstallationLock lock )
        implements AutoCloseable {

    private static final Duration GATE_POLL = Duration.ofMillis( 100 );

    /**
     * Validates the components.
     *
     * @throws NullPointerException
     *         if a component is null
     */
    public InstallationOwnership {
        requireNonNull( gate, "gate must not be null" );
        requireNonNull( lock, "lock must not be null" );
    }

    /**
     * Takes ownership for a regular start: the gate first, then the installation lock, never waiting.
     * When the installation lock is busy the gate is released again before returning.
     *
     * @param installation
     *         canonical installation root
     * @return both locks, or an empty optional when another start or a running panel owns the installation
     * @throws NullPointerException
     *         if the installation is null
     */
    public static @Nonnull Optional< InstallationOwnership > tryTake( @Nonnull Path installation ) {
        requireNonNull( installation, "installation must not be null" );
        Optional< InstallationLock > gate = InstallationLock.tryAcquire( installation,
                InstallationLock.Kind.STARTUP_GATE );
        if ( gate.isEmpty() ) {
            log.info( "Another start of this installation is in progress" );
            return Optional.empty();
        }
        Optional< InstallationLock > lock = InstallationLock.tryAcquire( installation,
                InstallationLock.Kind.INSTALLATION );
        if ( lock.isEmpty() ) {
            gate.get().close();
            log.info( "Another process owns this installation" );
            return Optional.empty();
        }
        return Optional.of( new InstallationOwnership( gate.get(), lock.get() ) );
    }

    /**
     * Completes ownership after a handoff: waits for the predecessor's real exit, which frees the gate it
     * kept, and then takes the gate. The installation lock is closed when this fails.
     *
     * @param accepted
     *         accepted handoff holding the installation lock
     * @param timeout
     *         bound of each wait
     * @return both locks
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalStateException
     *         if the predecessor keeps running or the gate stays busy
     */
    public static @Nonnull InstallationOwnership afterHandoff( @Nonnull AcceptedHandoff accepted,
                                                              @Nonnull Duration timeout ) {
        requireNonNull( accepted, "accepted must not be null" );
        requireNonNull( timeout, "timeout must not be null" );
        InstallationLock lock = accepted.lock();
        try {
            if ( !HandoffProtocol.awaitExit( accepted.predecessor(), timeout ) ) {
                throw new IllegalStateException( "The predecessor did not exit in time" );
            }
            long deadline = System.nanoTime() + timeout.toNanos();
            while ( true ) {
                Optional< InstallationLock > gate = InstallationLock.tryAcquire( lock.getInstallation(),
                        InstallationLock.Kind.STARTUP_GATE );
                if ( gate.isPresent() ) {
                    return new InstallationOwnership( gate.get(), lock );
                }
                if ( System.nanoTime() - deadline >= 0 ) {
                    throw new IllegalStateException( "The startup gate stayed busy" );
                }
                Thread.sleep( GATE_POLL );
            }
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            lock.close();
            throw new IllegalStateException( "Interrupted while waiting for the startup gate", exception );
        } catch ( RuntimeException exception ) {
            lock.close();
            throw exception;
        }
    }

    /** Releases the installation lock and then the gate. */
    @Override
    public void close() {
        lock.close();
        gate.close();
    }
}
