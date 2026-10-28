package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletionStage;

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
 * <p>A successfully created server may outlive the panel. Failed graceful stop never grants force by
 * itself: force needs a manually confirmed Kill or, while the panel shuts down, an explicit shutdown Kill
 * chosen by the panel close strategy (AD-031). Closing runtime resources never terminates the process.
 * A durable launch token and installation lock prevent duplicate launches; adopting a surviving process
 * requires explicit identity verification.
 */
public interface ServerRuntime {

    /**
     * Starts a process in a minimal start-only runtime. The complete production runtime rejects this
     * overload because it requires the world context supplied to {@link #start(LaunchSpec, WorldContext)}.
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
     * Starts a launch with its captured world context, which is required for data-safe stop and recovery.
     *
     * @param launchSpec
     *         complete process specification
     * @param world
     *         immutable world and separately authorized backup directory
     * @return correlated starting status
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalStateException
     *         if a process or recovery decision already blocks start
     */
    @Nonnull
    ServerStatus start( @Nonnull LaunchSpec launchSpec, @Nonnull WorldContext world );

    /**
     * Accepts a stop on the lifecycle queue, then performs I/O asynchronously.
     *
     * <pre>
     * Stop: RUNNING/STOP_FAILED -&gt; complete snapshot -&gt; CTRL+C -&gt; one retry -&gt; observed exit
     * Kill: RUNNING/STOP_FAILED -&gt; snapshot attempt (failure tolerated) -&gt; force -&gt; observed exit
     * Success -&gt; STOPPED; failure with a live process -&gt; STOP_FAILED
     * </pre>
     * The total deadline is five minutes. Rejection is synchronous; accepted work completes only
     * after observed exit, or exceptionally while retaining a live process. Shutdown never grants force.
     * Confirmed Kill tolerates snapshot failures but never bypasses cancellation, deadline or identity checks.
     * After {@link #shutdown()} only shutdown requests are accepted, including
     * {@link StopRequest#shutdownKill(String)} for a process left alive by failed graceful shutdown.
     *
     * @param request
     *         launch-specific authorization
     * @return completion of the accepted operation
     * @throws NullPointerException
     *         if the request is null
     * @throws IllegalStateException
     *         if the state, launch identifier, or active operation refuses the request
     */
    @Nonnull
    CompletionStage< Void > stop( @Nonnull StopRequest request );

    /**
     * Blocks new launches and gracefully stops, or joins, the currently managed process. Force afterwards
     * requires a separate {@link StopRequest#shutdownKill(String)}.
     *
     * @return completion without any implicit force permission
     */
    @Nonnull
    CompletionStage< Void > shutdown();

    /**
     * Explicitly adopts the saved, reverified launch after a panel restart.
     *
     * @param launchId
     *         saved launch selected by the administrator
     * @throws NullPointerException
     *         if the identifier is null
     * @throws IllegalStateException
     *         if recovery is unavailable or the identifier is stale
     */
    void adopt( @Nonnull String launchId );

    /**
     * Returns the current snapshot of the managed server. Never blocks on the runtime's thread, so it
     * is safe to call while rendering a page.
     *
     * @return immutable snapshot of the lifecycle state and, where applicable, the managed process
     */
    @Nonnull
    ServerStatus status();
}
