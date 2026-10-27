package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessageIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of console texts the updater can show. The wording lives in the console translation bundle.
 */
enum UpdaterMessage implements ConsoleMessageIf {

    /**
     * The command line of the updater is invalid.
     */
    INVALID_INVOCATION( "updater.invalidInvocation" ),

    /**
     * Another start or a running panel owns the installation.
     */
    ALREADY_RUNNING( "updater.alreadyRunning" ),

    /**
     * The installation could not be taken over from the launcher.
     */
    HANDOFF_FAILED( "updater.handoffFailed" ),

    /**
     * The update is skipped because the Valheim server may still run.
     */
    SERVER_POSSIBLY_ACTIVE( "updater.serverPossiblyActive" ),

    /**
     * The update is being installed; argument: new version.
     */
    INSTALLING( "updater.installing" ),

    /**
     * The transaction could not be prepared; the installed version starts.
     */
    PREPARATION_FAILED( "updater.preparationFailed" ),

    /**
     * The update was committed; argument: new version.
     */
    UPDATED( "updater.updated" ),

    /**
     * An interrupted update is being rolled back.
     */
    RECOVERING( "updater.recovering" ),

    /**
     * The update failed and the previous version was restored.
     */
    ROLLED_BACK( "updater.rolledBack" ),

    /**
     * The transaction could neither be finished nor rolled back.
     */
    REPAIR_NEEDED( "updater.repairNeeded" ),

    /**
     * The installed launcher could not be started.
     */
    LAUNCHER_START_FAILED( "updater.launcherStartFailed" );

    private final String key;

    UpdaterMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Returns the translation key of this message.
     *
     * @return key resolved against the console translation bundle
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
