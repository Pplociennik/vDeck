package com.github.pplociennik.vdeck.runtime.api;

/**
 * Lifecycle states of the single Valheim server process managed by vDeck.
 *
 * <p>This enum declares only the states the implemented start path can reach:
 *
 * <pre>
 * STOPPED --- start ---&gt; STARTING --- process stayed alive ---&gt; RUNNING
 *                            |                                     |
 *                            | process exited                      | process exited
 *                            v                                     v
 *                       START_FAILED                            CRASHED
 * </pre>
 *
 * <p>The stopping half of the lifecycle agreed in {@code design.md} §8 — {@code STOPPING},
 * {@code STOP_FAILED} and {@code RECOVERY_REQUIRED} — is added together with the features that can
 * actually produce those states, so no constant exists that no code can reach.
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
    CRASHED;

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