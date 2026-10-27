package com.github.pplociennik.vdeck.runtime.installation;

/**
 * Role of the successor process named in a handoff descriptor. A successor accepts only a descriptor
 * naming its own role, so a descriptor written for the panel can never authorize an updater.
 */
public enum HandoffRole {

    /**
     * Updater started from the executor to apply a prepared update.
     */
    UPDATER,

    /**
     * Updater started from the executor to recover an interrupted transaction.
     */
    RECOVERY,

    /**
     * Installed launcher started after an update or recovery.
     */
    LAUNCHER,

    /**
     * Panel started by the launcher; it keeps the installation lock while it runs.
     */
    PANEL
}
