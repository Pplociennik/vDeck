package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.UpdatePreparationException;
import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.HandoffResult;
import com.github.pplociennik.vdeck.runtime.installation.HandoffRole;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateJournalHeader;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Verifies the start decisions of the launcher with stubbed process and network boundaries.
 */
@ExtendWith( MockitoExtension.class )
class VdeckLauncherTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion INSTALLED = new ReleaseVersion( 1, 1, 0 );

    private static final String[] START = { "--", "--debug" };

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

    @Mock
    private UpdatePreparation preparation;

    @Mock
    private Process panel;

    private UpdateLayout layout;

    private VdeckLauncher underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a launcher of release 1.1.0 for an empty installation.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        underTest = launcher( Optional.of( INSTALLED ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that without a newer release the panel starts with the settings and forwarded arguments,
     * and that its exit code becomes the launcher's.
     */
    @Test
    void shouldStartPanel_whenNoUpdateIsAvailable() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.empty() );
        givenPanelExits( 0 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( command( HandoffRole.PANEL ) ).contains( "--server.address=127.0.0.1", "--server.port=8085",
                "--debug", "--enable-native-access=ALL-UNNAMED", "-Dvdeck.handoff=id" );
        then( console ).should().show( LauncherMessage.UPDATE_CHECK, INSTALLED );
    }

    /**
     * Verifies that a panel failure is reported and its exit code returned.
     */
    @Test
    void shouldReturnPanelExitCode_whenPanelFails() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.empty() );
        givenPanelExits( 3 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 3 );
        then( console ).should().showErrorAndWait( LauncherMessage.PANEL_EXITED, 3 );
    }

    /**
     * Verifies that a panel exiting before it takes the lock over, as after its own start check, keeps
     * the console window open until Enter and ends the start with exit code 1.
     */
    @Test
    void shouldWaitForEnter_whenPanelExitsBeforeHandoff() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.empty() );
        given( successors.handOff( any(), eq( HandoffRole.PANEL ), any() ) ).willAnswer( invocation -> {
            InstallationLock lock = invocation.getArgument( 0 );
            return new HandoffResult.Failed( Optional.of( lock ) );
        } );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( console ).should().showErrorAndWait( LauncherMessage.PANEL_START_FAILED );
    }

    /**
     * Verifies that disabled automatic updates never contact GitHub.
     */
    @Test
    void shouldSkipUpdateCheck_whenUpdatesAreDisabled() throws Exception {
        // GIVEN
        writeStartSettings( "update.enabled=false\n" );
        givenPanelExits( 0 );

        // WHEN
        underTest.run( LauncherArguments.parse( START ) );

        // THEN
        then( preparation ).shouldHaveNoInteractions();
        then( serverActivity ).shouldHaveNoInteractions();
        then( console ).should().show( LauncherMessage.UPDATES_DISABLED );
    }

    /**
     * Verifies that a development build never updates itself.
     */
    @Test
    void shouldSkipUpdateCheck_whenBuildIsNoRelease() throws Exception {
        // GIVEN
        underTest = launcher( Optional.empty() );
        givenPanelExits( 0 );

        // WHEN
        underTest.run( LauncherArguments.parse( START ) );

        // THEN
        then( preparation ).shouldHaveNoInteractions();
        then( console ).should().show( LauncherMessage.DEVELOPMENT_BUILD );
    }

    /**
     * Verifies that a possibly running server prevents any update check.
     */
    @Test
    void shouldSkipUpdateCheck_whenServerMayRun() throws Exception {
        // GIVEN
        given( serverActivity.isServerPossiblyActive() ).willReturn( true );
        givenPanelExits( 0 );

        // WHEN
        underTest.run( LauncherArguments.parse( START ) );

        // THEN
        then( preparation ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a prepared update hands the installation to the updater in the executor and ends the
     * launcher without starting the panel.
     */
    @Test
    void shouldHandOverToUpdater_whenUpdateIsPrepared() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.of( new ReleaseVersion( 1, 2, 0 ) ) );
        given( successors.handOff( any(), eq( HandoffRole.UPDATER ), any() ) )
                .willReturn( new HandoffResult.Accepted( mock( Process.class ) ) );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( command( HandoffRole.UPDATER ) ).contains( layout.executorJava().toString(),
                layout.executorUpdaterJar().toString(), "--handoff", "id", "--debug" ).doesNotContain( "--recover" );
        then( successors ).should( never() ).handOff( any(), eq( HandoffRole.PANEL ), any() );
    }

    /**
     * Verifies that a failed preparation never blocks the installed version.
     */
    @Test
    void shouldStartCurrentVersion_whenPreparationFails() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willThrow( new UpdatePreparationException( new IOException( "net" ),
                true ) );
        Files.createDirectories( layout.stagedDirectory() );
        givenPanelExits( 0 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( layout.stagedDirectory() ).doesNotExist();
    }

    /**
     * Verifies that an updater that does not take over falls back to the installed panel with the lock
     * regained by the starter.
     */
    @Test
    void shouldStartCurrentVersion_whenUpdaterDoesNotTakeOver() throws Exception {
        // GIVEN
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.of( new ReleaseVersion( 1, 2, 0 ) ) );
        given( successors.handOff( any(), eq( HandoffRole.UPDATER ), any() ) ).willAnswer( invocation -> {
            InstallationLock lock = invocation.getArgument( 0 );
            return new HandoffResult.Failed( Optional.of( lock ) );
        } );
        givenPanelExits( 0 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        then( successors ).should().handOff( any(), eq( HandoffRole.PANEL ), any() );
    }

    /**
     * Verifies that an interrupted transaction is handed to recovery before settings or network are used.
     */
    @Test
    void shouldStartRecovery_whenJournalIsActive() throws Exception {
        // GIVEN
        writeJournal( UpdateState.APPLYING );
        Files.createDirectories( layout.executorJava().getParent() );
        Files.writeString( layout.executorJava(), "java" );
        Files.writeString( layout.executorUpdaterJar(), "jar" );
        given( successors.handOff( any(), eq( HandoffRole.RECOVERY ), any() ) )
                .willReturn( new HandoffResult.Accepted( mock( Process.class ) ) );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( command( HandoffRole.RECOVERY ) ).contains( "--recover" );
        then( preparation ).shouldHaveNoInteractions();
        assertThat( layout.journal() ).exists();
    }

    /**
     * Verifies that an interrupted transaction without its executor stops the start.
     */
    @Test
    void shouldStop_whenRecoveryCopyIsMissing() throws Exception {
        // GIVEN
        writeJournal( UpdateState.APPLYING );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( successors ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a finished transaction is archived and the panel starts without another check.
     */
    @Test
    void shouldCleanUpAndStartPanel_whenTransactionHasEnded() throws Exception {
        // GIVEN
        writeJournal( UpdateState.COMMITTED );
        Files.createDirectories( layout.executorDirectory() );
        given( preparation.prepare( INSTALLED ) ).willReturn( Optional.empty() );
        givenPanelExits( 0 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isZero();
        assertThat( layout.journal() ).doesNotExist();
        assertThat( layout.executorDirectory() ).doesNotExist();
    }

    /**
     * Verifies that invalid start settings stop the start before anything else.
     */
    @Test
    void shouldStop_whenStartSettingsAreInvalid() throws Exception {
        // GIVEN
        writeStartSettings( "panel.port=0\n" );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( START ) );

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        then( console ).should().showErrorAndWait( LauncherMessage.INVALID_START_SETTING, "panel.port" );
        then( successors ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a running panel prevents a second one and any cleanup.
     */
    @Test
    void shouldRefuse_whenInstallationIsOwned() throws Exception {
        // GIVEN
        Files.createDirectories( layout.stagedDirectory() );

        // WHEN
        int exitCode;
        try ( InstallationLock running = InstallationLock.acquire( layout.installation(),
                InstallationLock.Kind.INSTALLATION ) ) {
            exitCode = underTest.run( LauncherArguments.parse( START ) );
        }

        // THEN
        assertThat( exitCode ).isEqualTo( 1 );
        assertThat( layout.stagedDirectory() ).exists();
        then( successors ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that after an update the launcher takes over from the updater and skips the update check.
     */
    @Test
    void shouldSkipUpdateCheck_whenStartedAfterUpdate() throws Exception {
        // GIVEN
        given( protocol.accept( eq( "id" ), eq( HandoffRole.LAUNCHER ), any() ) ).willAnswer( invocation ->
                new com.github.pplociennik.vdeck.runtime.installation.AcceptedHandoff(
                        InstallationLock.acquire( layout.installation(), InstallationLock.Kind.INSTALLATION ),
                        new com.github.pplociennik.vdeck.runtime.api.ProcessIdentity( 4_000_000_000L,
                                java.time.Instant.EPOCH, layout.resolve( "java.exe" ) ) ) );
        givenPanelExits( 0 );

        // WHEN
        int exitCode = underTest.run( LauncherArguments.parse( new String[] { "--after-update", "--handoff", "id" } ) );

        // THEN
        assertThat( exitCode ).isZero();
        then( preparation ).shouldHaveNoInteractions();
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

    private VdeckLauncher launcher( Optional< ReleaseVersion > version ) {
        return new VdeckLauncher( layout, console, serverActivity, protocol, successors, version,
                installed -> preparation );
    }

    private void givenPanelExits( int code ) throws Exception {
        given( panel.waitFor() ).willReturn( code );
        given( successors.handOff( any(), eq( HandoffRole.PANEL ), any() ) )
                .willReturn( new HandoffResult.Accepted( panel ) );
    }

    @SuppressWarnings( "unchecked" )
    private List< String > command( HandoffRole role ) {
        ArgumentCaptor< Function > captor = ArgumentCaptor.forClass( Function.class );
        then( successors ).should().handOff( any(), eq( role ), captor.capture() );
        return ( List< String > ) captor.getValue().apply( "id" );
    }

    private void writeStartSettings( String contents ) throws Exception {
        Path file = layout.resolve( ".vDeck/config/vDeck-start.properties" );
        Files.createDirectories( file.getParent() );
        Files.writeString( file, contents );
    }

    private void writeJournal( UpdateState state ) throws Exception {
        Files.createDirectories( layout.updateDirectory() );
        Properties values = new Properties();
        new UpdateJournalHeader( UUID.randomUUID(), state ).writeTo( values );
        try ( var output = Files.newOutputStream( layout.journal() ) ) {
            values.store( output, null );
        }
    }
}
