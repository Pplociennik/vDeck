package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of {@link SuccessorStarter#handOff}: either the successor owns the installation, or the handoff
 * failed and the caller learns whether it still (or again) holds the installation lock.
 */
public sealed interface HandoffResult permits HandoffResult.Accepted, HandoffResult.Failed {

    /**
     * The successor acknowledged the handoff and now owns the installation lock.
     *
     * @param successor
     *         running successor process
     */
    record Accepted( @Nonnull Process successor ) implements HandoffResult {

        /**
         * Validates the component.
         *
         * @throws NullPointerException
         *         if the successor is null
         */
        public Accepted {
            requireNonNull( successor, "successor must not be null" );
        }
    }

    /**
     * The handoff failed; any started successor has exited.
     *
     * @param lock
     *         installation lock held by the caller again, or empty when it could not be regained and the
     *         caller must not change the installation
     */
    record Failed( @Nonnull Optional< InstallationLock > lock ) implements HandoffResult {

        /**
         * Validates the component.
         *
         * @throws NullPointerException
         *         if the optional is null
         */
        public Failed {
            requireNonNull( lock, "lock must not be null" );
        }
    }
}
