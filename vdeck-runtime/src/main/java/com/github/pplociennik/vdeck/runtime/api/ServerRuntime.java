package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

/**
 * Public facade of the vDeck runtime and the <strong>single owner of everything the application knows
 * about the running server</strong>: its lifecycle state, the managed process and that launch's log
 * file.
 *
 * <p>Exactly one implementation instance exists per panel, because vDeck manages at most one Valheim
 * server ({@code design.md} §7). The application registers it once as a singleton bean and every
 * consumer injects this interface. <strong>No other component may keep a copy of the process
 * identifier, the lifecycle state or the running server.</strong> A cached copy is a second source of
 * truth that silently goes stale the moment the server exits on its own, so callers ask
 * {@link #status()} again instead of remembering an earlier answer.
 *
 * <p>Implementations serialise every state change onto one thread, so concurrent HTTP requests cannot
 * interleave a start with a process-exit notification. All methods are safe to call from any thread.
 *
 * <h2>The server's lifetime is bound to the panel's</h2>
 *
 * <p>A server started by vDeck never outlives vDeck. The process is placed in a job object that the
 * operating system tears down when the panel's last handle to it closes, which happens whether the
 * panel exits normally, is killed or crashes. Two consequences follow, and both are deliberate:
 *
 * <ul>
 *   <li>the panel never has to look for a server left over from a previous run, so there is no PID
 *       file, no process scan at startup and no adoption of a foreign process — the only server that
 *       exists is the one this instance started and still holds;</li>
 *   <li>termination through the job object is <strong>forcible</strong>. It is not the graceful
 *       CTRL+C shutdown Valheim prefers, so a panel that crashes takes the world's unsaved progress
 *       with it. A normal shutdown is expected to stop the server gracefully first; the job object is
 *       the safety net for the paths where that is impossible.</li>
 * </ul>
 */
public interface ServerRuntime {

    /**
     * Starts the Valheim server described by the specification and returns the state the panel reached.
     *
     * <p>The call is serialised against every other lifecycle event and against itself, so two
     * administrators pressing Start at the same moment cannot create two processes. It returns once the
     * process exists, not once the server is ready to accept players:
     *
     * <pre>
     * state startable ----&gt; create process ----&gt; STARTING
     *        |                    |                  |
     *        | no                 | refused          | still alive after the grace period
     *        v                    v                  v
     *  IllegalStateException  START_FAILED        RUNNING
     * </pre>
     *
     * <p>A process that exits before the grace period elapses leaves {@link ServerState#START_FAILED};
     * one that exits later leaves {@link ServerState#CRASHED}. Both transitions happen on the runtime's
     * own thread, so the status returned here may already be superseded when the caller reads it —
     * which is exactly why no caller may cache it.
     *
     * @param launchSpec
     *         complete, validated description of the process to create
     * @return status reached by this call, normally {@link ServerState#STARTING}
     * @throws NullPointerException
     *         if {@code launchSpec} is {@code null}
     * @throws IllegalStateException
     *         if a server is already starting or running, so this request would create a second one
     */
    @Nonnull
    ServerStatus start( @Nonnull LaunchSpec launchSpec );

    /**
     * Returns the current snapshot of the managed server. Never blocks on the runtime's thread, so it
     * is safe to call while rendering a page.
     *
     * @return immutable snapshot of the lifecycle state and, where applicable, the managed process
     */
    @Nonnull
    ServerStatus status();
}