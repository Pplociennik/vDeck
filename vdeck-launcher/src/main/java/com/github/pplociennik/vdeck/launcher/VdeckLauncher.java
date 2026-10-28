package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.UpdateCleanup.JournalStatus;
import com.github.pplociennik.vdeck.launcher.exc.InvalidStartSettingException;
import com.github.pplociennik.vdeck.launcher.exc.UpdatePreparationException;
import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffResult;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.installation.InstallationOwnership;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import com.github.pplociennik.vdeck.runtime.installation.RunLogRetention;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Starts vDeck: the only program the start script runs (AD-024, AD-026).
 *
 * <pre>
 * take ownership: gate + installation lock (or a handoff from the updater, then the gate)
 *   |
 *   +-- active or unreadable journal --> hand over to recovery in the executor, exit
 *   +-- finished journal              --> archive it, delete work files
 *   +-- no journal                    --> delete unpublished work files
 *   v
 * read vDeck-start.properties (invalid value: stop)
 *   |
 *   +-- update.enabled, release build, no --after-update, server stopped:
 *   |      prepare newer release (30 s stall, 60 min ceiling) --> hand over to the updater, exit
 *   |      any failure                    --> continue with the installed version
 *   v
 * hand the installation lock to the panel, keep the gate, wait for the panel and return its exit code
 * </pre>
 *
 * <p>An update problem never prevents the installed version from starting. Only invalid start settings,
 * another owner of the installation, an unrecoverable transaction or a failed handoff stop the start.
 */
@Log4j2
final class VdeckLauncher {

    private final UpdateLayout layout;

    private final UserConsole console;

    private final ServerActivityCheck serverActivity;

    private final HandoffProtocol protocol;

    private final SuccessorStarter successors;

    private final UpdateCleanup cleanup;

    private final Optional< ReleaseVersion > installedVersion;

    private final UpdatePreparationFactory preparations;

    /**
     * Creates the launcher.
     *
     * @param layout
     *         update layout of the canonical installation
     * @param console
     *         user console
     * @param serverActivity
     *         check for a running Valheim server
     * @param protocol
     *         handoff protocol of the installation
     * @param successors
     *         starts the panel, updater and recovery
     * @param installedVersion
     *         version of this launcher; empty for development builds, which never update
     * @param preparations
     *         creates the update preparation for the installed version
     */
    VdeckLauncher( @Nonnull UpdateLayout layout, @Nonnull UserConsole console,
                   @Nonnull ServerActivityCheck serverActivity, @Nonnull HandoffProtocol protocol,
                   @Nonnull SuccessorStarter successors, @Nonnull Optional< ReleaseVersion > installedVersion,
                   @Nonnull UpdatePreparationFactory preparations ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
        this.serverActivity = requireNonNull( serverActivity, "serverActivity must not be null" );
        this.protocol = requireNonNull( protocol, "protocol must not be null" );
        this.successors = requireNonNull( successors, "successors must not be null" );
        this.installedVersion = requireNonNull( installedVersion, "installedVersion must not be null" );
        this.preparations = requireNonNull( preparations, "preparations must not be null" );
        this.cleanup = new UpdateCleanup( layout );
    }

    /**
     * Executes one start.
     *
     * @param arguments
     *         parsed command line
     * @return exit code of the panel, 0 after handing over to the updater or recovery, 1 on failure
     * @throws NullPointerException
     *         if the arguments are null
     */
    int run( @Nonnull LauncherArguments arguments ) {
        requireNonNull( arguments, "arguments must not be null" );

        Optional< InstallationOwnership > ownership = takeOwnership( arguments );
        if ( ownership.isEmpty() ) {
            return 1;
        }

        try ( InstallationOwnership owned = ownership.get() ) {
            return start( owned.lock(), arguments );
        }
    }

    private Optional< InstallationOwnership > takeOwnership( LauncherArguments arguments ) {
        if ( arguments.handoffId().isEmpty() ) {
            Optional< InstallationOwnership > owned = InstallationOwnership.tryTake( layout.installation() );
            if ( owned.isEmpty() ) {
                console.showErrorAndWait( LauncherMessage.ALREADY_RUNNING );
            }
            return owned;
        }

        try {
            return Optional.of( InstallationOwnership.afterHandoff(
                    protocol.accept( arguments.handoffId().get(), HandoffRole.LAUNCHER, HandoffProtocol.TIMEOUT ),
                    HandoffProtocol.TIMEOUT ) );
        } catch ( IllegalArgumentException | IllegalStateException exception ) {
            log.error( "Could not take over the installation from the updater", exception );
            console.showErrorAndWait( LauncherMessage.HANDOFF_FAILED );
            return Optional.empty();
        }
    }

    private int start( InstallationLock lock, LauncherArguments arguments ) {
        RunLogRetention.deleteExpired( layout.installation(), "launcher", LocalDate.now() );
        protocol.discardStale();

        JournalStatus journal = cleanup.inspect();
        if ( journal == JournalStatus.ACTIVE ) {
            return startRecovery( lock, arguments.panelArguments() );
        }
        cleanUpPreviousUpdate( journal );

        Optional< StartSettings > settings = loadSettings();
        if ( settings.isEmpty() ) {
            return 1;
        }

        if ( !shouldCheckForUpdate( arguments, settings.get() ) ) {
            return runPanel( lock, settings.get(), arguments.panelArguments() );
        }
        UpdateAttempt attempt = tryUpdate( lock, arguments.panelArguments() );
        return attempt.exitCode()
                .orElseGet( () -> runPanel( attempt.lock(), settings.get(), arguments.panelArguments() ) );
    }

    private void cleanUpPreviousUpdate( JournalStatus journal ) {
        if ( journal == JournalStatus.TERMINAL ) {
            cleanup.finishTerminal();
        } else {
            cleanup.discardUnpublished();
        }
    }

    private Optional< StartSettings > loadSettings() {
        try {
            return Optional.of( StartSettings.load( layout.installation() ) );
        } catch ( InvalidStartSettingException exception ) {
            console.showErrorAndWait( LauncherMessage.INVALID_START_SETTING, exception.getKey() );
            return Optional.empty();
        } catch ( IOException exception ) {
            log.error( "Could not read the start settings", exception );
            console.showErrorAndWait( LauncherMessage.START_SETTINGS_UNREADABLE );
            return Optional.empty();
        }
    }

    private boolean shouldCheckForUpdate( LauncherArguments arguments, StartSettings settings ) {
        if ( arguments.afterUpdate() ) {
            log.info( "Started after an update; no further update check" );
            return false;
        }
        if ( !settings.updateEnabled() ) {
            log.info( "Automatic updates are disabled in the start settings" );
            console.show( LauncherMessage.UPDATES_DISABLED );
            return false;
        }
        if ( installedVersion.isEmpty() ) {
            log.info( "Development build; automatic updates are skipped" );
            console.show( LauncherMessage.DEVELOPMENT_BUILD );
            return false;
        }
        if ( serverActivity.isServerPossiblyActive() ) {
            console.show( LauncherMessage.SERVER_POSSIBLY_ACTIVE );
            return false;
        }
        return true;
    }

    private UpdateAttempt tryUpdate( InstallationLock lock, List< String > panelArguments ) {
        Optional< ReleaseVersion > prepared = prepareUpdate();
        if ( prepared.isEmpty() ) {
            return UpdateAttempt.continueWith( lock );
        }

        console.show( LauncherMessage.UPDATE_DOWNLOADED, prepared.get() );
        HandoffResult result = successors.handOff( lock, HandoffRole.UPDATER,
                operationId -> executorCommand( operationId, false, panelArguments ) );

        return switch ( result ) {
            case HandoffResult.Accepted accepted -> UpdateAttempt.exit( 0 );
            case HandoffResult.Failed failed when failed.lock().isPresent() -> {
                cleanup.discardUnpublished();
                console.show( LauncherMessage.UPDATER_START_FAILED );
                yield UpdateAttempt.continueWith( failed.lock().get() );
            }
            case HandoffResult.Failed failed -> {
                console.showErrorAndWait( LauncherMessage.UPDATER_NOT_RESPONDING );
                yield UpdateAttempt.exit( 1 );
            }
        };
    }

    private Optional< ReleaseVersion > prepareUpdate() {
        ReleaseVersion installed = installedVersion.orElseThrow();
        log.info( "Checking for an update of the installed version {}", installed );
        console.show( LauncherMessage.UPDATE_CHECK, installed );

        try {
            return preparations.create( installed ).prepare( installed );
        } catch ( UpdatePreparationException exception ) {
            console.show( LauncherMessage.DOWNLOAD_FAILED );
            if ( exception.isWorkerStopped() ) {
                cleanup.discardUnpublished();
            }
            return Optional.empty();
        }
    }

    private int startRecovery( InstallationLock lock, List< String > panelArguments ) {
        if ( !Files.isRegularFile( layout.executorJava(), NOFOLLOW_LINKS )
                || !Files.isRegularFile( layout.executorUpdaterJar(), NOFOLLOW_LINKS ) ) {
            console.showErrorAndWait( LauncherMessage.REPAIR_NEEDED );
            return 1;
        }

        console.show( LauncherMessage.RECOVERY_STARTING );
        HandoffResult result = successors.handOff( lock, HandoffRole.RECOVERY,
                operationId -> executorCommand( operationId, true, panelArguments ) );

        if ( result instanceof HandoffResult.Failed failed ) {
            failed.lock().ifPresent( InstallationLock::close );
            console.showErrorAndWait( LauncherMessage.RECOVERY_START_FAILED );
            return 1;
        }
        return 0;
    }

    private int runPanel( InstallationLock lock, StartSettings settings, List< String > panelArguments ) {
        HandoffResult result = successors.handOff( lock, HandoffRole.PANEL,
                operationId -> panelCommand( operationId, settings, panelArguments ) );
        if ( !( result instanceof HandoffResult.Accepted accepted ) ) {
            ( ( HandoffResult.Failed ) result ).lock().ifPresent( InstallationLock::close );
            console.showErrorAndWait( LauncherMessage.PANEL_START_FAILED );
            return 1;
        }

        int exitCode = awaitPanel( accepted.successor() );

        log.info( "Panel exited with code {}", exitCode );
        if ( exitCode != 0 ) {
            console.showErrorAndWait( LauncherMessage.PANEL_EXITED, exitCode );
        }
        return exitCode;
    }

    private static int awaitPanel( Process panel ) {
        // CTRL+C reaches both processes; keep the gate until the panel has shut down gracefully.
        Runtime.getRuntime().addShutdownHook( new Thread( () -> awaitShutdown( panel ), "vdeck-panel-wait" ) );
        try {
            return panel.waitFor();
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private List< String > executorCommand( String operationId, boolean recover, List< String > panelArguments ) {
        List< String > command = new ArrayList<>();
        command.add( layout.executorJava().toString() );
        command.addAll( UpdateLayout.SMALL_JVM_OPTIONS );
        command.add( "-D" + UpdateLayout.SERVER_DIRECTORY_PROPERTY + "=" + layout.installation() );
        command.add( "-jar" );
        command.add( layout.executorUpdaterJar().toString() );
        if ( recover ) {
            command.add( "--recover" );
        }
        command.add( "--handoff" );
        command.add( operationId );
        command.add( "--" );
        command.addAll( panelArguments );
        return command;
    }

    private List< String > panelCommand( String operationId, StartSettings settings, List< String > panelArguments ) {
        List< String > command = new ArrayList<>();
        command.add( layout.installedJava().toString() );
        command.add( "--enable-native-access=ALL-UNNAMED" );
        command.add( "-D" + UpdateLayout.SERVER_DIRECTORY_PROPERTY + "=" + layout.installation() );
        command.add( "-D" + UpdateLayout.PANEL_HANDOFF_PROPERTY + "=" + operationId );
        command.add( "-D" + UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY + "=true" );
        command.add( "-jar" );
        command.add( layout.resolve( UpdateLayout.PANEL_JAR ).toString() );
        command.add( "--server.address=" + settings.address() );
        command.add( "--server.port=" + settings.port() );
        command.addAll( panelArguments );
        return command;
    }

    private static void awaitShutdown( Process panel ) {
        try {
            // Longer than the panel's own graceful shutdown limit in application.yaml.
            panel.waitFor( 7, TimeUnit.MINUTES );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates the update preparation for the installed version.
     */
    @FunctionalInterface
    interface UpdatePreparationFactory {

        /**
         * Creates a preparation.
         *
         * @param installed
         *         installed release version
         * @return new preparation with its own HTTP client
         */
        @Nonnull
        UpdatePreparation create( @Nonnull ReleaseVersion installed );
    }

    /**
     * Result of an update attempt: either the launcher exits, or it continues with the given lock.
     *
     * @param exitCode
     *         exit code when the launcher must stop
     * @param lock
     *         installation lock to continue with; meaningless when an exit code is present
     */
    private record UpdateAttempt( Optional< Integer > exitCode, InstallationLock lock ) {

        static UpdateAttempt exit( int code ) {
            return new UpdateAttempt( Optional.of( code ), null );
        }

        static UpdateAttempt continueWith( InstallationLock lock ) {
            return new UpdateAttempt( Optional.empty(), lock );
        }
    }
}
