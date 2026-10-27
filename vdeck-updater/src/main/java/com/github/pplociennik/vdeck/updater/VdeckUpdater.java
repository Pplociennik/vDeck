package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffResult;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationOwnership;
import com.github.pplociennik.vdeck.runtime.installation.JarManifests;
import com.github.pplociennik.vdeck.runtime.installation.RunLogRetention;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Applies a prepared update or recovers an interrupted one, then hands the installation to the
 * installed launcher (AD-024). Runs only from the independent executor runtime
 * ({@code .vDeck/update/executor}), so it can replace the installed {@code jre} and {@code lib}.
 *
 * <pre>
 * take ownership (handoff from the launcher, or gate + lock when the start script runs --recover)
 *   |
 *   +-- journal exists or --recover --> recover: terminal -> keep; otherwise roll back
 *   +-- otherwise --> server may run? -> no changes
 *                     plan + merge settings -> PREPARED -> APPLYING -> validate -> COMMITTED
 *                                                  | failure
 *                                                  v
 *                                     ROLLING_BACK -> ROLLED_BACK   (failure: manual repair, no panel)
 *   |
 *   v
 * hand off to the installed launcher with --after-update (it cleans up and starts the panel)
 * </pre>
 *
 * <p>The executor, the staged package and the backup are never deleted here: the launcher removes them
 * only after this process has exited and the journal is terminal.
 */
@Log4j2
final class VdeckUpdater {

    private static final String REPAIR_MESSAGE = """
            Nie udało się bezpiecznie dokończyć ani cofnąć aktualizacji vDeck. Panel nie zostanie uruchomiony.
            Oryginalne pliki są w katalogu .vDeck\\update\\backup, a przebieg w .vDeck\\logs\\updater.
            Sposób naprawy opisuje instrukcja w rozdziale o aktualizacjach.""";

    private final UpdateLayout layout;

    private final UserConsole console;

    private final ServerActivityCheck serverActivity;

    private final HandoffProtocol protocol;

    private final SuccessorStarter successors;

    private final String ownVersion;

    private final Checkpoint checkpoint;

    private final TransactionFiles files;

    /**
     * Creates the updater.
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
     *         starts the installed launcher
     * @param ownVersion
     *         version of this updater, which is the version being installed
     * @param checkpoint
     *         observation point of the transaction
     */
    VdeckUpdater( @Nonnull UpdateLayout layout, @Nonnull UserConsole console,
                  @Nonnull ServerActivityCheck serverActivity, @Nonnull HandoffProtocol protocol,
                  @Nonnull SuccessorStarter successors, @Nonnull String ownVersion, @Nonnull Checkpoint checkpoint ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
        this.serverActivity = requireNonNull( serverActivity, "serverActivity must not be null" );
        this.protocol = requireNonNull( protocol, "protocol must not be null" );
        this.successors = requireNonNull( successors, "successors must not be null" );
        this.ownVersion = requireNonNull( ownVersion, "ownVersion must not be null" );
        this.checkpoint = requireNonNull( checkpoint, "checkpoint must not be null" );
        this.files = new TransactionFiles( layout );
    }

    /**
     * Executes one updater run.
     *
     * @param arguments
     *         parsed command line
     * @return process exit code: 0 after a successful handoff to the launcher, 1 otherwise
     * @throws NullPointerException
     *         if the arguments are null
     */
    int run( @Nonnull UpdaterArguments arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        Optional< InstallationOwnership > ownership = takeOwnership( arguments );
        if ( ownership.isEmpty() ) {
            return 1;
        }
        try ( InstallationOwnership owned = ownership.get() ) {
            RunLogRetention.deleteExpired( layout.installation(), "updater", LocalDate.now() );
            protocol.discardStale();
            Outcome outcome = arguments.recover() || files.exists( layout.journal() ) ? recover() : apply();
            log.info( "Updater finished with outcome {}", outcome );
            if ( outcome == Outcome.REPAIR_NEEDED ) {
                console.showErrorAndWait( REPAIR_MESSAGE );
                return 1;
            }
            return startLauncher( owned, arguments.panelArguments() );
        }
    }

    private Optional< InstallationOwnership > takeOwnership( UpdaterArguments arguments ) {
        if ( arguments.handoffId().isEmpty() ) {
            Optional< InstallationOwnership > owned = InstallationOwnership.tryTake( layout.installation() );
            if ( owned.isEmpty() ) {
                console.showErrorAndWait( "vDeck jest już uruchomiony albo trwa jego uruchamianie. "
                        + "Zamknij panel i uruchom skrypt ponownie." );
            }
            return owned;
        }
        HandoffRole role = arguments.recover() ? HandoffRole.RECOVERY : HandoffRole.UPDATER;
        try {
            return Optional.of( InstallationOwnership.afterHandoff(
                    protocol.accept( arguments.handoffId().get(), role, HandoffProtocol.TIMEOUT ),
                    HandoffProtocol.TIMEOUT ) );
        } catch ( IllegalArgumentException | IllegalStateException exception ) {
            log.error( "Could not take over the installation", exception );
            console.show( "Nie udało się przejąć instalacji od launchera; aktualizacja nie została rozpoczęta." );
            return Optional.empty();
        }
    }

    private Outcome apply() {
        if ( serverActivity.isServerPossiblyActive() ) {
            console.show( "Serwer Valheim może nadal działać, więc aktualizacja została pominięta." );
            return Outcome.NO_CHANGES;
        }
        console.show( "Instalowanie aktualizacji vDeck " + ownVersion + "..." );
        UpdateJournal journal;
        try {
            DurableFiles.deleteTree( layout.installation(), layout.backupDirectory() );
            List< JournalOperation > operations = new UpdatePlan( layout, files, new PropertiesMerger() ).prepare();
            journal = UpdateJournal.create( layout.journal(), installedVersion(), ownVersion, operations, checkpoint );
        } catch ( IOException | RuntimeException exception ) {
            log.error( "Could not prepare the update transaction", exception );
            if ( files.exists( layout.journal() ) ) {
                return recover();
            }
            console.show( "Nie udało się przygotować aktualizacji. Zostanie uruchomiona dotychczasowa wersja." );
            return Outcome.NO_CHANGES;
        }
        try {
            new FileReplacer( files, checkpoint ).apply( journal );
            verifyInstalled( journal );
            journal.changeState( UpdateState.COMMITTED );
            console.show( "Zaktualizowano vDeck do wersji " + ownVersion + "." );
            return Outcome.COMMITTED;
        } catch ( IOException | RuntimeException exception ) {
            log.error( "Update {} failed; rolling back", journal.getUpdateId(), exception );
            return rollBack( journal );
        }
    }

    private Outcome recover() {
        if ( !files.exists( layout.journal() ) ) {
            log.info( "No update transaction to recover" );
            return Outcome.NO_CHANGES;
        }
        UpdateJournal journal;
        try {
            journal = UpdateJournal.read( layout.journal(), checkpoint );
        } catch ( IOException | RuntimeException exception ) {
            log.error( "The update journal cannot be trusted", exception );
            return Outcome.REPAIR_NEEDED;
        }
        if ( journal.getState().isTerminal() ) {
            log.info( "Update {} already ended as {}", journal.getUpdateId(), journal.getState() );
            return journal.getState() == UpdateState.COMMITTED ? Outcome.COMMITTED : Outcome.ROLLED_BACK;
        }
        console.show( "Wykryto przerwaną aktualizację vDeck. Przywracanie poprzedniej wersji..." );
        return rollBack( journal );
    }

    private Outcome rollBack( UpdateJournal journal ) {
        try {
            new UpdateRollback( files, checkpoint ).rollback( journal );
            console.show( "Aktualizacja nie powiodła się. Przywrócono poprzednią wersję vDeck." );
            return Outcome.ROLLED_BACK;
        } catch ( IOException | RuntimeException exception ) {
            log.error( "Rollback of update {} failed", journal.getUpdateId(), exception );
            return Outcome.REPAIR_NEEDED;
        }
    }

    private void verifyInstalled( UpdateJournal journal ) throws IOException {
        List< String > missing = DistributionItem.missingFiles( layout.installation() );
        if ( !missing.isEmpty() ) {
            throw new IOException( "The updated installation is incomplete: " + missing );
        }
        for ( JournalOperation operation : journal.getOperations() ) {
            if ( operation.newSha256() != null
                    && !operation.newSha256().equals( DurableFiles.sha256( files.installed( operation.item() ) ) ) ) {
                throw new IOException( "The installed " + operation.item() + " differs from the package" );
            }
        }
        if ( !Optional.of( ownVersion ).equals( JarManifests.attribute( layout.resolve( UpdateLayout.LAUNCHER_JAR ),
                JarManifests.IMPLEMENTATION_VERSION ) ) ) {
            throw new IOException( "The installed launcher has an unexpected version" );
        }
    }

    private String installedVersion() {
        try {
            return JarManifests.attribute( layout.resolve( UpdateLayout.LAUNCHER_JAR ),
                    JarManifests.IMPLEMENTATION_VERSION ).orElse( "unknown" );
        } catch ( IOException exception ) {
            log.warn( "Could not read the installed version", exception );
            return "unknown";
        }
    }

    private int startLauncher( InstallationOwnership owned, List< String > panelArguments ) {
        HandoffResult result = successors.handOff( owned.lock(), HandoffRole.LAUNCHER,
                operationId -> launcherCommand( operationId, panelArguments ) );
        if ( result instanceof HandoffResult.Failed failed ) {
            failed.lock().ifPresent( lock -> lock.close() );
            console.showErrorAndWait( "Nie udało się uruchomić launchera vDeck. Uruchom skrypt start-vDeck.bat "
                    + "ponownie." );
            return 1;
        }
        return 0;
    }

    private List< String > launcherCommand( String operationId, List< String > panelArguments ) {
        List< String > command = new ArrayList<>();
        command.add( layout.installedJava().toString() );
        command.addAll( UpdateLayout.SMALL_JVM_OPTIONS );
        command.add( "-D" + UpdateLayout.SERVER_DIRECTORY_PROPERTY + "=" + layout.installation() );
        command.add( "-jar" );
        command.add( layout.resolve( UpdateLayout.LAUNCHER_JAR ).toString() );
        command.add( "--after-update" );
        command.add( "--handoff" );
        command.add( operationId );
        command.add( "--" );
        command.addAll( panelArguments );
        return command;
    }

    /**
     * Result of the transaction part of a run.
     */
    private enum Outcome {
        NO_CHANGES,
        COMMITTED,
        ROLLED_BACK,
        REPAIR_NEEDED
    }
}
