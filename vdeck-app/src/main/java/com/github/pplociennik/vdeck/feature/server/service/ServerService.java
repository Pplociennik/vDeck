package com.github.pplociennik.vdeck.feature.server.service;

import com.github.pplociennik.vdeck.feature.server.exc.ServerAdoptException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerKillException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStopException;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import jakarta.annotation.Nonnull;

/**
 * The panel's entry point to controlling the Valheim server. Everything the web layer knows about the
 * server passes through here; it never reaches the runtime directly.
 */
public interface ServerService {

    /**
     * Name of the Valheim server executable, fixed by the publisher's installation layout. It sits
     * next to {@code vdeck.jar} in the installation directory.
     */
    String EXECUTABLE_NAME = "valheim_server.exe";

    /**
     * Name of the generated mirror script, relative to the installation directory. It is written on
     * every start and is never executed by the panel, nor shown in the interface.
     */
    String START_SCRIPT_NAME = ".vDeck/vDeck-server-start.bat";

    /**
     * Starts the Valheim server described by the stored launch profile.
     *
     * <p>Checks come first and have no side effects, so a refused start changes nothing:
     *
     * <pre>
     * already starting or running? -----------------&gt; rejected
     * pre-start checks -----------------------------&gt; rejected, naming the reason
     * reserve this launch's log file
     * build the command line from the profile
     * write the mirror script                          failure here only warns
     * hand the launch to the runtime ---------------&gt; failed, keeping the cause
     * </pre>
     *
     * <p>Returns as soon as the process exists, not when the server is ready for players. Whether the
     * launch then becomes {@code RUNNING} or {@code START_FAILED} is decided by the runtime afterwards,
     * so callers read {@link #getStatus()} again rather than assuming anything.
     *
     * @throws ServerStartException
     *         if a check refuses the launch, or the process cannot be created
     */
    void start();

    /**
     * Admits an asynchronous graceful stop without force permission.
     *
     * @param launchId
     *         launch selected in the submitted island
     * @throws NullPointerException
     *         if the identifier is null
     * @throws ServerStopException
     *         if admission fails or the form is stale
     */
    void stop( @Nonnull String launchId );

    /**
     * Admits immediate Kill after explicit acceptance of data-loss risk for this launch.
     * The runtime first attempts a snapshot, then forces termination even if the snapshot fails.
     *
     * @param launchId
     *         launch from the confirmation screen
     * @param confirmed
     *         explicit acceptance, validated again on the server
     * @throws NullPointerException
     *         if the identifier is null
     * @throws ServerKillException
     *         if risk was not accepted or admission fails
     */
    void kill( @Nonnull String launchId, boolean confirmed );

    /**
     * Explicitly restores management of a saved, reverified server after panel restart.
     *
     * @param launchId
     *         saved launch offered by the runtime
     * @throws NullPointerException
     *         if the identifier is null
     * @throws ServerAdoptException
     *         if recovery is rejected or fails
     */
    void adopt( @Nonnull String launchId );

    /**
     * Returns what the runtime currently knows about the managed server. The result is a snapshot that
     * may already be out of date, so it is read again rather than remembered.
     *
     * @return current lifecycle snapshot
     */
    @Nonnull
    ServerStatus getStatus();
}
