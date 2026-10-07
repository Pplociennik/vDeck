package com.github.pplociennik.vdeck.runtime.api;



/**
 * Lifecycle states of the single Valheim server process managed by vDeck.
 *
 * <p>Starting, stopping, failed-stop and recovery states block a new launch. Failed stop retains
 * the managed process; only observed exit completes a stop. Recovery requires an explicit decision.
 */
public enum ServerState {

    /**
     * No server process is managed. The initial state of a freshly started panel.
     */
    STOPPED,

    /**
     * The process has been created but has not yet been confirmed to keep running.
     */
    STARTING,

    /**
     * The process is alive and managed by the panel.
     */
    RUNNING,

    /**
     * The process exited while still starting, so the server never became usable.
     */
    START_FAILED,

    /**
     * The process exited on its own while it was running, without the panel asking for it.
     */
    CRASHED,

    /** A snapshot or graceful signal is in progress; conflicting commands are refused. */
    STOPPING,

    /** Stop failed and the still-live process remains managed; a new launch is prohibited. */
    STOP_FAILED,

    /** A previous or externally started server must be resolved before a new launch. */
    RECOVERY_REQUIRED;

    /**
     * Tells whether an administrator may request Stop or a manually confirmed Kill.
     *
     * @return true for running and failed-stop processes only
     */
    public boolean isStoppable() {
        return this == RUNNING || this == STOP_FAILED;
    }

    /**
     * Tells whether a start may be requested while the server is in this state. A terminal failure is
     * startable, because the administrator's way out of it is to launch the server again.
     *
     * @return {@code true} for {@link #STOPPED}, {@link #START_FAILED} and {@link #CRASHED},
     *         {@code false} while a process is already being started or is running
     */
    public boolean isStartable() {
        return this == STOPPED || this == START_FAILED || this == CRASHED;
    }
}
