package com.github.pplociennik.vdeck.runtime.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Verifies the observable contracts of the server-stop implementation in isolation. */
class ManagedLaunchStoreTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    @TempDir
    private Path directory;

    private ManagedLaunchStore underTest;

    private Path installation;

    private Path backups;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a fresh isolated fixture. */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.resolve( "installation" ) );
        Files.createDirectory( installation.resolve( "save" ) );
        backups = installation.resolve( ".vDeck/shutdown-snapshots" );
        underTest = new ManagedLaunchStore( installation );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================
    /** Releases the resources owned by this fixture. */
    @AfterEach
    void tearDown() {
        underTest.close();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================


    /** Persists the exact world captured at launch without arguments or secrets. */
    @Test
    void shouldRoundTripCapturedWorld_whenTokenIsWritten() throws Exception {
        // GIVEN
        ManagedLaunch launch = new ManagedLaunch( "launch-id", new ProcessIdentity( 42, Instant.EPOCH,
                installation.resolve( "valheim_server.exe" ) ),
                new WorldContext( "OriginalWorld", installation.resolve( "save" ), backups ),
                installation.resolve( "run.log" ) );
        // WHEN
        underTest.write( launch );
        var result = underTest.read();
        // THEN
        assertThat( result ).contains( launch );
        assertThat( Files.readString( installation.resolve( ".vDeck/server.pid" ) ) ).doesNotContain( "password" );
    }

    /** Missing metadata is an explicit absence. */
    @Test
    void shouldReturnNoLaunch_whenTokenIsAbsent() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var result = underTest.read();
        // THEN
        assertThat( result ).isEmpty();
    }

    /** Refuses a second panel before any lifecycle operation can start. */
    @Test
    void shouldRefuseAnotherPanel_whenInstallationIsLocked() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> new ManagedLaunchStore( installation ) );
        // THEN
        assertThat( failure ).isInstanceOf( java.nio.channels.OverlappingFileLockException.class );
    }

    /** Releases only the panel lock when resources close. */
    @Test
    void shouldAllowAnotherPanel_whenLockIsReleased() throws Exception {
        // GIVEN
        underTest.close();
        // WHEN
        underTest = new ManagedLaunchStore( installation );
        // THEN
        assertThat( underTest.executable() ).isEqualTo( installation.resolve( "valheim_server.exe" ) );
    }

    /** Refuses untrusted token content rather than guessing a process. */
    @Test
    void shouldRefuseToken_whenMetadataIsMalformed() throws Exception {
        // GIVEN
        Files.writeString( installation.resolve( ".vDeck/server.pid" ), "pid=42\n" );
        // WHEN
        var failure = catchThrowable( underTest::read );
        // THEN
        assertThat( failure ).isInstanceOf( RuntimeException.class );
    }

    /** Bounds token input before property parsing. */
    @Test
    void shouldRefuseToken_whenInputIsTooLarge() throws Exception {
        // GIVEN
        Files.writeString( installation.resolve( ".vDeck/server.pid" ), "x".repeat( 16385 ) );
        // WHEN
        var failure = catchThrowable( underTest::read );
        // THEN
        assertThat( failure ).isInstanceOf( java.io.UncheckedIOException.class );
    }

    /** Clears only the obsolete token after an observed process exit. */
    @Test
    void shouldPreserveLockFile_whenTokenIsCleared() throws Exception {
        // GIVEN
        Files.writeString( installation.resolve( ".vDeck/server.pid" ), "obsolete" );
        // WHEN
        underTest.clear();
        // THEN
        assertThat( installation.resolve( ".vDeck/server.pid" ) ).doesNotExist();
        assertThat( installation.resolve( ".vDeck/vdeck.lock" ) ).exists();
    }

    /** Rejects null input before token persistence. */
    @Test
    void shouldRejectNull_whenLaunchIsMissing() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> underTest.write( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
    /** Recovery reads preserve valid process identity even if the original save directory was removed. */
    @Test
    void shouldReadSavedLaunch_whenSaveDirectoryNoLongerExists() throws Exception {
        // GIVEN
        ManagedLaunch launch = new ManagedLaunch( "launch-id", new ProcessIdentity( 42, Instant.EPOCH,
                installation.resolve( "valheim_server.exe" ) ),
                new WorldContext( "World", installation.resolve( "save" ), backups ),
                installation.resolve( "run.log" ) );
        underTest.write( launch );
        Files.delete( installation.resolve( "save" ) );
        // WHEN
        var result = underTest.read();
        // THEN
        assertThat( result ).contains( launch );
    }

    /** Quarantine retains the malformed token's exact contents and never touches the installation lock. */
    @Test
    void shouldPreserveInvalidToken_whenQuarantined() throws Exception {
        // GIVEN
        Files.writeString( installation.resolve( ".vDeck/server.pid" ), "malformed" );
        // WHEN
        underTest.quarantine();
        // THEN
        assertThat( installation.resolve( ".vDeck/server.pid" ) ).doesNotExist();
        assertThat( installation.resolve( ".vDeck/vdeck.lock" ) ).exists();
        try ( var files = Files.newDirectoryStream( installation.resolve( ".vDeck" ), "server.pid.invalid-*" ) ) {
            var iterator = files.iterator();
            assertThat( iterator.hasNext() ).isTrue();
            assertThat( iterator.next() ).hasContent( "malformed" );
            assertThat( iterator.hasNext() ).isFalse();
        }
    }

    /** Unavailable quarantine is surfaced so the runtime cannot treat a failed move as recovery. */
    @Test
    void shouldFailQuarantine_whenTokenIsMissing() {
        // GIVEN
        // No recovery token exists.
        // WHEN
        var failure = catchThrowable( underTest::quarantine );
        // THEN
        assertThat( failure ).isInstanceOf( java.io.UncheckedIOException.class );
    }

    /** The installation is required before the constructor opens a lock channel. */
    @Test
    void shouldRejectMissingInstallation_whenConstructedWithNull() {
        // GIVEN
        // The fixture already owns its own lock.
        // WHEN
        var failure = catchThrowable( () -> new ManagedLaunchStore( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    /** A token cannot redirect snapshots of an adopted server outside the panel's .vDeck directory. */
    @Test
    void shouldRefuseToken_whenSnapshotDirectoryLeavesPanelDirectory() throws Exception {
        // GIVEN
        ManagedLaunch launch = new ManagedLaunch( "launch-id", new ProcessIdentity( 42, Instant.EPOCH,
                installation.resolve( "valheim_server.exe" ) ),
                new WorldContext( "World", installation.resolve( "save" ), directory.resolve( "backups" ) ),
                installation.resolve( "run.log" ) );
        underTest.write( launch );
        // WHEN
        var failure = catchThrowable( underTest::read );
        // THEN
        assertThat( failure ).isInstanceOf( java.io.UncheckedIOException.class );
    }

}
