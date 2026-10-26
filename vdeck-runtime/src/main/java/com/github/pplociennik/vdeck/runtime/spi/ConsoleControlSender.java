package com.github.pplociennik.vdeck.runtime.spi;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import jakarta.annotation.Nonnull;

import java.time.Duration;

/** Boundary running an isolated console helper; successful return means a signal, never a saved world. */
@FunctionalInterface
public interface ConsoleControlSender {

    /**
     * Sends CTRL+C through an isolated helper with bounded waiting and no inherited output pipes.
     *
     * @param identity
     *         verified identity the helper must recheck
     * @param timeout
     *         maximum helper lifetime
     * @throws NullPointerException
     *         if either argument is null
     * @throws RuntimeException
     *         if helper creation, signal, cleanup, or timeout handling fails
     */
    void send( @Nonnull ProcessIdentity identity, @Nonnull Duration timeout );
}
