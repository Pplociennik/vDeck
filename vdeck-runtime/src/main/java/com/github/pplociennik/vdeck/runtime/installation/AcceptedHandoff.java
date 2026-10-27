package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Result of a successful handoff on the successor side. The successor owns the lock from now on and must
 * close it; the predecessor identity lets it wait for the predecessor's real exit.
 *
 * @param lock
 *         installation lock taken over from the predecessor
 * @param predecessor
 *         verified identity of the process that published the descriptor
 */
public record AcceptedHandoff( @Nonnull InstallationLock lock, @Nonnull ProcessIdentity predecessor ) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException
     *         if a component is null
     */
    public AcceptedHandoff {
        requireNonNull( lock, "lock must not be null" );
        requireNonNull( predecessor, "predecessor must not be null" );
    }
}
