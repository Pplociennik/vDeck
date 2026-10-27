package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffResult;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Verifies the updater's decisions between apply, recovery, refusal and repair.
 */
@ExtendWith( MockitoExtension.class )
class VdeckUpdaterTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String[] APPLY = { "--", "--debug" };

    private static final String[] RECOVER = { "--recover" };

    @TempDir
    private Path directory;

    @Mock
    private UserConsole console;

    @Mock
    private ServerActivityCheck serverActivity;

    @Mock
    private HandoffProtocol protocol;

    @Mock
    private SuccessorStarter successors;

    private UpdateLayout layout;

    private VdeckUpdater underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation of 1.0.0 and an updater of version 1.1.0.
     */
    @BeforeEach
    void setUp() throws Exception {
        Path installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        DistributionFixture.writeDistribution( installation, "1.0.0", "kept=custom\nobsolete=old\n" );
        layout = new UpdateLayout( installation );
        underTest = new VdeckUpdater( layout, console, serverActivity, protocol, successors, "1.1.0", Checkpoint.NONE );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies a complete update: new files and merged settings are installed, the journal is committed
     * and the installed launcher takes over with the forwarded panel arguments.
     */
    @Test
    void shouldCommitAndStartLauncher_whenStagedUpdateIsApplied() throws Exception {
        // GIVEN
        DistributionFixture.writeDistribution( layout.stagedDirectory(), "1.1.0", "kept=default\nadded=new\n" );
        givenLauncherAccepts();

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( APPLY ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( layout.resolve( "README.md" ) ).hasContent( "README.md of 1.1.0" );
        assertThat( layout.resolve( ".vDeck/config/vDeck.properties" ) ).hasContent( "kept=custom\nadded=new\n" );
        assertThat( readState() ).isEqualTo( UpdateState.COMMITTED );
        assertThat( launcherCommand() ).contains( "--after-update", "--", "--debug" )
                .contains( layout.resolve( UpdateLayout.LAUNCHER_JAR ).toString() );
    }

    /**
     * Verifies that a possibly running server leaves the installation untouched while the panel still starts.
     */
    @Test
    void shouldSkipUpdate_whenServerMayRun() throws Exception {
        // GIVEN
        DistributionFixture.writeDistribution( layout.stagedDirectory(), "1.1.0", "kept=default\n" );
        Map< String, String > before = DistributionFixture.snapshot( layout.installation() );
        given( serverActivity.isServerPossiblyActive() ).willReturn( true );
        givenLauncherAccepts();

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( APPLY ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( DistributionFixture.snapshot( layout.installation() ) ).isEqualTo( before );
        assertThat( layout.journal() ).doesNotExist();
    }

    /**
     * Verifies that an incomplete staged package changes nothing and the current version starts.
     */
    @Test
    void shouldKeepInstallation_whenStagedPackageIsIncomplete() throws Exception {
        // GIVEN
        Map< String, String > before = DistributionFixture.snapshot( layout.installation() );
        givenLauncherAccepts();

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( APPLY ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( DistributionFixture.snapshot( layout.installation() ) ).isEqualTo( before );
        then( console ).should().show( "Nie udało się przygotować aktualizacji. Zostanie uruchomiona dotychczasowa "
                + "wersja." );
    }

    /**
     * Verifies that recovery rolls an interrupted transaction back before the launcher starts.
     */
    @Test
    void shouldRollBack_whenRecoveringInterruptedTransaction() throws Exception {
        // GIVEN
        Path installation = layout.installation();
        DistributionFixture.Transaction transaction = interruptedTransaction();
        givenLauncherAccepts();

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( RECOVER ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( DistributionFixture.snapshot( installation ) ).isEqualTo( transaction.original() );
        assertThat( readState() ).isEqualTo( UpdateState.ROLLED_BACK );
    }

    /**
     * Verifies that a finished transaction is not touched again by recovery.
     */
    @Test
    void shouldOnlyStartLauncher_whenJournalIsTerminal() throws Exception {
        // GIVEN
        DistributionFixture.Transaction transaction = interruptedTransaction();
        transaction.journal().changeState( UpdateState.ROLLING_BACK );
        new UpdateRollback( transaction.files(), Checkpoint.NONE ).rollback( transaction.journal() );
        Map< String, String > before = DistributionFixture.snapshot( layout.installation() );
        givenLauncherAccepts();

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( RECOVER ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( DistributionFixture.snapshot( layout.installation() ) ).isEqualTo( before );
    }

    /**
     * Verifies that an untrustworthy journal stops the start with repair instructions.
     */
    @Test
    void shouldRequireRepair_whenJournalIsInvalid() throws Exception {
        // GIVEN
        Files.createDirectories( layout.updateDirectory() );
        Files.writeString( layout.journal(), "protocol=1\nstate=APPLYING\n" );

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( RECOVER ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( console ).should().showErrorAndWait( anyString() );
        then( successors ).should( never() ).handOff( any(), any(), any() );
    }

    /**
     * Verifies that a running panel blocks recovery without any change.
     */
    @Test
    void shouldRefuse_whenInstallationIsOwned() throws Exception {
        // GIVEN
        Files.createDirectories( layout.updateDirectory() );
        Files.writeString( layout.journal(), "protocol=1\n" );

        // WHEN
        int exitCode;
        try ( InstallationLock panel = InstallationLock.acquire( layout.installation(),
                InstallationLock.Kind.INSTALLATION ) ) {
            exitCode = underTest.run( UpdaterArguments.parse( RECOVER ) );
        }

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        assertThat( layout.journal() ).hasContent( "protocol=1\n" );
        then( successors ).should( never() ).handOff( any(), any(), any() );
    }

    /**
     * Verifies that a rejected handoff from the launcher changes nothing.
     */
    @Test
    void shouldRefuse_whenHandoffIsInvalid() {
        // GIVEN
        given( protocol.accept( eq( "id" ), eq( HandoffRole.UPDATER ), any( Duration.class ) ) )
                .willThrow( new IllegalStateException( "foreign descriptor" ) );

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( new String[] { "--handoff", "id" } ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( successors ).should( never() ).handOff( any(), any(), any() );
    }

    /**
     * Verifies that a launcher that does not take over ends the run with an error.
     */
    @Test
    void shouldFail_whenLauncherDoesNotTakeOver() {
        // GIVEN
        given( serverActivity.isServerPossiblyActive() ).willReturn( true );
        given( successors.handOff( any(), eq( HandoffRole.LAUNCHER ), any() ) )
                .willReturn( new HandoffResult.Failed( Optional.empty() ) );

        // WHEN
        int exitCode = underTest.run( UpdaterArguments.parse( APPLY ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( console ).should().showErrorAndWait( anyString() );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentsAreNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.run( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenLauncherAccepts() {
        given( successors.handOff( any(), eq( HandoffRole.LAUNCHER ), any() ) )
                .willReturn( new HandoffResult.Accepted( mock( Process.class ) ) );
    }

    @SuppressWarnings( "unchecked" )
    private List< String > launcherCommand() {
        ArgumentCaptor< Function > captor = ArgumentCaptor.forClass( Function.class );
        then( successors ).should().handOff( any(), eq( HandoffRole.LAUNCHER ), captor.capture() );
        return ( List< String > ) captor.getValue().apply( "id" );
    }

    private UpdateState readState() throws Exception {
        return UpdateJournal.read( layout.journal(), Checkpoint.NONE ).getState();
    }

    /**
     * Applies an update without committing it, as if the updater had crashed afterwards.
     */
    private DistributionFixture.Transaction interruptedTransaction() throws Exception {
        DistributionFixture.Transaction transaction = DistributionFixture.prepareTransaction( layout.installation(),
                Checkpoint.NONE );
        new FileReplacer( transaction.files(), Checkpoint.NONE ).apply( transaction.journal() );
        return transaction;
    }
}
