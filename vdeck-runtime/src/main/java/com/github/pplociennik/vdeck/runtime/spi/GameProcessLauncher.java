package com.github.pplociennik.vdeck.runtime.spi;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;

/**
 * Boundary between the runtime and the operating system's process creation. The only implementation
 * today targets Windows; a second platform adds its own without touching the lifecycle code, and tests
 * substitute a fake so that no test ever creates a real process.
 *
 * <p>An implementation creates the process and returns immediately. It does not wait for the server to
 * become usable, does not interpret its output and does not decide what the resulting state is — that
 * belongs to the lifecycle layer.
 */
public interface GameProcessLauncher {

    /**
     * Creates the Valheim server process described by the specification.
     *
     * <p>The process is detached from the panel's console, so a CTRL+C reaching the terminal that
     * started vDeck does not reach the server. Console streams are not redirected: the server writes
     * its own log to {@link LaunchSpec#logFile()}, which keeps logging independent of the panel's
     * lifetime ({@code design.md} §6).
     *
     * @param launchSpec
     *         complete, validated description of the process to create
     * @return the newly created process together with its persisted identity
     * @throws NullPointerException
     *         if {@code launchSpec} is {@code null}
     * @throws UncheckedIOException
     *         if the operating system refuses to create the process; no process is left behind
     */
    @Nonnull
    RunningServer launch( @Nonnull LaunchSpec launchSpec );
}