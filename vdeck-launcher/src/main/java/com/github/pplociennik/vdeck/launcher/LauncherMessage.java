package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessageIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of console texts the launcher can show. The wording lives in the console translation bundle.
 */
enum LauncherMessage implements ConsoleMessageIf {

    /**
     * The command line of the launcher is invalid.
     */
    INVALID_INVOCATION( "launcher.invalidInvocation" ),

    /**
     * Another start or a running panel owns the installation.
     */
    ALREADY_RUNNING( "launcher.alreadyRunning" ),

    /**
     * The installation could not be taken over from the updater.
     */
    HANDOFF_FAILED( "launcher.handoffFailed" ),

    /**
     * An interrupted update needs recovery, but the executor files are missing.
     */
    REPAIR_NEEDED( "launcher.repairNeeded" ),

    /**
     * Recovery of an interrupted update is being started.
     */
    RECOVERY_STARTING( "launcher.recoveryStarting" ),

    /**
     * Recovery could not be started.
     */
    RECOVERY_START_FAILED( "launcher.recoveryStartFailed" ),

    /**
     * A start setting has an invalid value; argument: the setting key.
     */
    INVALID_START_SETTING( "launcher.invalidStartSetting" ),

    /**
     * The start settings file cannot be read.
     */
    START_SETTINGS_UNREADABLE( "launcher.startSettingsUnreadable" ),

    /**
     * Automatic updates are disabled in the start settings.
     */
    UPDATES_DISABLED( "launcher.updatesDisabled" ),

    /**
     * The update check is skipped because the installed build is a development version.
     */
    DEVELOPMENT_BUILD( "launcher.developmentBuild" ),

    /**
     * The update check is skipped because the Valheim server may still run.
     */
    SERVER_POSSIBLY_ACTIVE( "launcher.serverPossiblyActive" ),

    /**
     * The update check starts; argument: installed version.
     */
    UPDATE_CHECK( "launcher.updateCheck" ),

    /**
     * No newer release exists; argument: installed version.
     */
    NO_NEWER_VERSION( "launcher.noNewerVersion" ),

    /**
     * A newer release was found and is being downloaded; argument: its version.
     */
    NEWER_VERSION_FOUND( "launcher.newerVersionFound" ),

    /**
     * Download progress; arguments: received MB, total MB, percent and MB per second.
     */
    DOWNLOAD_PROGRESS( "launcher.downloadProgress" ),

    /**
     * The update could not be prepared; the installed version starts.
     */
    DOWNLOAD_FAILED( "launcher.downloadFailed" ),

    /**
     * The update is prepared and the updater starts; argument: prepared version.
     */
    UPDATE_DOWNLOADED( "launcher.updateDownloaded" ),

    /**
     * The updater could not be started; the installed version starts.
     */
    UPDATER_START_FAILED( "launcher.updaterStartFailed" ),

    /**
     * The updater neither confirmed the handoff nor released the lock.
     */
    UPDATER_NOT_RESPONDING( "launcher.updaterNotResponding" ),

    /**
     * The panel could not be started.
     */
    PANEL_START_FAILED( "launcher.panelStartFailed" ),

    /**
     * Address of the running panel; arguments: host and port.
     */
    PANEL_ADDRESS( "launcher.panelAddress" ),

    /**
     * Asks to keep the console window open while the panel runs.
     */
    KEEP_WINDOW_OPEN( "launcher.keepWindowOpen" ),

    /**
     * The panel exited with an error; argument: exit code.
     */
    PANEL_EXITED( "launcher.panelExited" );

    private final String key;

    LauncherMessage( @Nonnull String key ) {
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
