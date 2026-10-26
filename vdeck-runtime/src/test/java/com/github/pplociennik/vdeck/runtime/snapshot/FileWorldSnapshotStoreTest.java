package com.github.pplociennik.vdeck.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.runtime.api.WorldSaveMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Verifies the observable contracts of the server-stop implementation in isolation. */
class FileWorldSnapshotStoreTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    @TempDir
    private Path directory;

    private FileWorldSnapshotStore underTest;

    private WorldContext world;

    private Path source;

    private Path backups;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a fresh isolated fixture. */
    @BeforeEach
    void setUp() throws Exception {
        // Production receives canonical paths; a short 8.3 temp path (e.g. RUNNER~1) would look redirected.
        directory = directory.toRealPath();
        underTest = new FileWorldSnapshotStore( directory, file -> file.toString() );
        source = Files.createDirectories( directory.resolve( "save/worlds_local" ) );
        backups = Files.createDirectory( directory.resolve( ".vDeck" ) ).resolve( "shutdown-snapshots" );
        world = new WorldContext( "Dedicated", source.getParent(), backups );
        Files.writeString( source.resolve( "Dedicated.db" ), "world-database" );
        Files.writeString( source.resolve( "Dedicated.fwl" ), "world-metadata" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================


    /** Checks complete pair publication and reproducible checksums. */
    @Test
    void shouldPublishCompletePair_whenWorldIsStable() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        underTest.create( world, "operation", () -> true );
        // THEN
        Path published = backups.resolve( "snapshot-latest" );
        assertThat( published.resolve( "Dedicated.db" ) ).hasContent( "world-database" );
        assertThat( published.resolve( "Dedicated.fwl" ) ).hasContent( "world-metadata" );
        Properties metadata = new Properties();
        try ( var input = Files.newInputStream( published.resolve( "snapshot.properties" ) ) ) {
            metadata.load( input );
        }
        assertThat( metadata.getProperty( "trust" ) ).isEqualTo( "BEST_EFFORT" );
        assertThat( metadata.getProperty( "sha256.Dedicated.db" ) ).isEqualTo( HexFormat.of().formatHex(
                MessageDigest.getInstance( "SHA-256" ).digest( "world-database".getBytes() ) ) );
        assertThat( backups ).isDirectoryContaining( path -> path.getFileName().toString().equals( "snapshot-latest" ) );
    }

    /** Copies every file in a directory-format world without touching another world. */
    @Test
    void shouldCopyWholeDirectory_whenWorldUsesDirectoryFormat() throws Exception {
        // GIVEN
        Path nested = Files.createDirectories( source.resolve( "Dedicated/regions" ) );
        Files.writeString( nested.resolve( "chunk.bin" ), "chunk-data" );
        Files.writeString( source.resolve( "Other.db" ), "unrelated" );
        // WHEN
        underTest.create( world, "directory", () -> true );
        // THEN
        assertThat( backups.resolve( "snapshot-latest/Dedicated/regions/chunk.bin" ) ).hasContent( "chunk-data" );
        assertThat( backups.resolve( "snapshot-latest/Other.db" ) ).doesNotExist();
    }

    /** Includes available old files in the same complete set. */
    @Test
    void shouldCopyPreviousSave_whenOldFilesExist() throws Exception {
        // GIVEN
        Files.writeString( source.resolve( "Dedicated.db.old" ), "previous-world" );
        // WHEN
        underTest.create( world, "old", () -> true );
        // THEN
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db.old" ) ).hasContent( "previous-world" );
    }

    /** Refuses an incomplete pair before publishing a snapshot. */
    @Test
    void shouldRefuseSnapshot_whenWorldMetadataIsMissing() throws Exception {
        // GIVEN
        Files.delete( source.resolve( "Dedicated.fwl" ) );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "missing", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( failure ).isInstanceOf( WorldSaveMissingException.class );
        assertThat( backups.resolve( "snapshot-latest" ) ).doesNotExist();
    }

    /** A metadata-only new world cannot be mistaken for a complete database backup. */
    @Test
    void shouldReportMissingSave_whenWorldHasNoDatabase() throws Exception {
        // GIVEN
        Files.delete( source.resolve( "Dedicated.db" ) );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "new-world", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( WorldSaveMissingException.class )
                .hasCauseInstanceOf( NoSuchFileException.class );
        assertThat( backups ).isEmptyDirectory();
        assertThat( source.resolve( "Dedicated.fwl" ) ).hasContent( "world-metadata" );
    }

    /** Missing source directories use the same diagnostic category as missing required files. */
    @Test
    void shouldReportMissingSave_whenWorldDirectoryDoesNotExist() {
        // GIVEN
        world = new WorldContext( "Dedicated", directory.resolve( "absent-save" ), backups );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "absent", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( WorldSaveMissingException.class );
        assertThat( backups ).isEmptyDirectory();
    }

    /** Storage failures must not claim that the source database is missing. */
    @Test
    void shouldReportStorageFailure_whenSnapshotRootIsAFile() throws Exception {
        // GIVEN
        Files.writeString( backups, "not-a-directory" );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "storage", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class )
                .isNotInstanceOf( WorldSaveMissingException.class );
        assertThat( backups ).hasContent( "not-a-directory" );
    }

    /** Snapshots are never created outside the panel's own .vDeck directory. */
    @Test
    void shouldRejectRoot_whenOutsidePanelDirectory() throws Exception {
        // GIVEN
        Path outside = directory.resolve( "shutdown-snapshots" );
        world = new WorldContext( "Dedicated", source.getParent(), outside );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "outside", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( outside ).doesNotExist();
    }

    /** Refuses an empty save rather than publishing a misleading success. */
    @Test
    void shouldRefuseSnapshot_whenWorldDataIsEmpty() throws Exception {
        // GIVEN
        Files.writeString( source.resolve( "Dedicated.db" ), "" );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "empty", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
    }

    /** Cancellation prevents publication and leaves no staging directory. */
    @Test
    void shouldCancelCopy_whenPermissionExpires() throws Exception {
        // GIVEN
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "cancel", () -> checks.incrementAndGet() < 3 ) );
        // THEN
        assertThat( failure ).isInstanceOf( CancellationException.class );
        try ( var files = Files.list( backups ) ) {
            assertThat( files.toList() ).isEmpty();
        }
    }

    /** Repeated stops retain the latest complete set without exhausting a history limit. */
    @Test
    void shouldReplacePreviousCopy_whenMoreThanThirtyTwoSnapshotsAreCreated() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        // WHEN
        for ( int index = 0; index < 33; index++ ) {
            Files.writeString( source.resolve( "Dedicated.db" ), "world-" + index );
            underTest.create( world, "operation-" + index, () -> true );
        }
        // THEN
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-32" );
        try ( var history = Files.list( backups ) ) {
            assertThat( history.map( path -> path.getFileName().toString() ).toList() )
                    .containsExactly( "snapshot-latest" );
        }
    }

    /** Rejects traversal in snapshot identifiers before any file is touched. */
    @Test
    void shouldRejectIdentifier_whenItEscapesRoot() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "../outside", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
        assertThat( backups ).doesNotExist();
    }

    /** Explicitly rejects every missing required boundary. */
    @Test
    void shouldRejectNull_whenArgumentIsMissing() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var worldFailure = catchThrowable( () -> underTest.create( null, "id", () -> true ) );
        var idFailure = catchThrowable( () -> underTest.create( world, null, () -> true ) );
        var guardFailure = catchThrowable( () -> underTest.create( world, "id", null ) );
        var directoryFailure = catchThrowable( () -> FileWorldSnapshotStore.checkDirectory( directory, null ) );
        var rootFailure = catchThrowable( () -> FileWorldSnapshotStore.checkDirectory( null, directory ) );
        var installationFailure = catchThrowable( () -> new FileWorldSnapshotStore( null, file -> "id" ) );
        var readerFailure = catchThrowable( () -> new FileWorldSnapshotStore( directory, null ) );
        // THEN
        assertThat( worldFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "world" );
        assertThat( idFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "operationId" );
        assertThat( guardFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "allowed" );
        assertThat( directoryFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "directory" );
        assertThat( rootFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "root" );
        assertThat( installationFailure ).isInstanceOf( NullPointerException.class );
        assertThat( readerFailure ).isInstanceOf( NullPointerException.class );
    }

    /** Failed copies never remove the previous complete snapshot. */
    @Test
    void shouldPreservePreviousSnapshot_whenNewWorldIsIncomplete() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        Files.delete( source.resolve( "Dedicated.db" ) );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "second", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( WorldSaveMissingException.class );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
    }

    /** Cancellation between publication renames restores the previous complete snapshot. */
    @Test
    void shouldRestorePreviousSnapshot_whenCancelledAfterMovingLatest() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        Files.writeString( source.resolve( "Dedicated.db" ), "replacement" );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "second",
                () -> Files.exists( backups.resolve( "snapshot-latest" ) ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( CancellationException.class );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
        try ( var entries = Files.list( backups ) ) {
            assertThat( entries.map( path -> path.getFileName().toString() ).toList() )
                    .containsExactly( "snapshot-latest" );
        }
    }

    /** Interrupted publication is recovered before any new copy or cleanup can discard the backup. */
    @Test
    void shouldRecoverPreviousSnapshot_whenCrashLeftOnlyOldDirectory() throws Exception {
        // GIVEN
        Path old = Files.createDirectories( backups.resolve( ".old-interrupted" ) );
        Files.writeString( old.resolve( "Dedicated.db" ), "last-good-copy" );
        Files.createDirectories( backups.resolve( ".tmp-interrupted/nested" ) );
        Files.delete( source.resolve( "Dedicated.db" ) );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "recovery", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( WorldSaveMissingException.class );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "last-good-copy" );
        assertThat( old ).doesNotExist();
        assertThat( backups.resolve( ".tmp-interrupted" ) ).doesNotExist();
    }

    /** Completed publication wins over old and temporary directories left after a crash. */
    @Test
    void shouldCleanCrashRemainders_whenLatestAlreadyExists() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        Files.createDirectory( backups.resolve( ".old-interrupted" ) );
        Files.createDirectory( backups.resolve( ".tmp-interrupted" ) );
        // WHEN
        underTest.create( world, "second", () -> true );
        // THEN
        try ( var entries = Files.list( backups ) ) {
            assertThat( entries.map( path -> path.getFileName().toString() ).toList() )
                    .containsExactly( "snapshot-latest" );
        }
        Properties metadata = new Properties();
        try ( var input = Files.newInputStream( backups.resolve( "snapshot-latest/snapshot.properties" ) ) ) {
            metadata.load( input );
        }
        assertThat( metadata.getProperty( "operation" ) ).isEqualTo( "second" );
    }

    /** Identity lookup fails closed and exhausts three bounded attempts without replacing the backup. */
    @Test
    void shouldRetainPreviousCopy_whenFileIdentityCannotBeRead() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        var attempts = new AtomicInteger();
        underTest = new FileWorldSnapshotStore( directory, file -> {
            attempts.incrementAndGet();
            throw new IOException( "Identity unavailable" );
        } );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "second", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( attempts ).hasValue( 3 );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
    }

    /** A replaced file with unchanged size and timestamps is detected by its identity alone. */
    @Test
    void shouldRefuseMixedWorld_whenFileIdentityChangesDuringEveryCopy() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        var calls = new AtomicInteger();
        underTest = new FileWorldSnapshotStore( directory, file -> "identity-" + calls.incrementAndGet() );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "second", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( calls ).hasValue( 12 );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
    }

    /** Relative paths and directories outside the trusted root are rejected without inspecting them. */
    @Test
    void shouldRejectDirectory_whenItLeavesTrustedRoot() {
        // GIVEN
        Path outside = directory.getParent();
        // WHEN
        var failure = catchThrowable( () -> FileWorldSnapshotStore.checkDirectory( directory, outside ) );
        var relative = catchThrowable( () -> FileWorldSnapshotStore.checkDirectory( directory, Path.of( "save" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( relative ).isInstanceOf( IOException.class );
    }

    /** Invalid installation roots fail before the writer can create a backup directory. */
    @Test
    void shouldRejectInstallation_whenRelativeOrMissing() {
        // GIVEN
        Path missing = directory.resolve( "absent-installation" );
        // WHEN
        var relative = catchThrowable( () -> new FileWorldSnapshotStore( Path.of( "relative" ), file -> "id" ) );
        var absent = catchThrowable( () -> new FileWorldSnapshotStore( missing, file -> "id" ) );
        // THEN
        assertThat( relative ).isInstanceOf( IllegalArgumentException.class );
        assertThat( absent ).isInstanceOf( UncheckedIOException.class );
        assertThat( backups ).doesNotExist();
    }

    /** A transient identity failure retries the whole set instead of publishing an unidentified file. */
    @Test
    void shouldRetryCompleteSet_whenFirstIdentityReadFails() throws Exception {
        // GIVEN
        var calls = new AtomicInteger();
        underTest = new FileWorldSnapshotStore( directory, file -> {
            if ( calls.incrementAndGet() == 1 ) {
                throw new IOException( "Transient identity failure" );
            }
            return file.toString();
        } );
        // WHEN
        underTest.create( world, "retried", () -> true );
        // THEN
        assertThat( calls ).hasValue( 5 );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
    }

    /**
     * Missing identities never fall back to fileKey or creation time.
     *
     * @param identity
     *         invalid result from the filesystem boundary
     */
    @ParameterizedTest( name = "[{index}] unavailable identity={0}" )
    @NullSource
    @ValueSource( strings = { "", " " } )
    void shouldFailClosed_whenIdentityReaderReturnsNoIdentity( String identity ) {
        // GIVEN
        underTest = new FileWorldSnapshotStore( directory, file -> identity );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "unidentified", () -> true ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( backups ).isEmptyDirectory();
    }

    /** A failed atomic publication rolls the old snapshot back into place. */
    @Test
    void shouldRestorePreviousCopy_whenStagingDisappearsBeforePublication() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        Files.writeString( source.resolve( "Dedicated.db" ), "replacement-world" );
        // WHEN
        var failure = catchThrowable( () -> underTest.create( world, "second", () -> {
            if ( !Files.exists( backups.resolve( "snapshot-latest" ) ) ) {
                try ( var entries = Files.newDirectoryStream( backups, ".tmp-*" ) ) {
                    Files.move( entries.iterator().next(), backups.resolve( "interfered-staging" ) );
                } catch ( IOException exception ) {
                    throw new UncheckedIOException( exception );
                }
            }
            return true;
        } ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "world-database" );
    }

    /** Failure to remove an obsolete entry cannot undo a successfully published snapshot. */
    @Test
    void shouldPublishLatest_whenObsoleteCleanupFails() throws Exception {
        // GIVEN
        underTest.create( world, "first", () -> true );
        Path obsolete = Files.writeString( backups.resolve( ".old-invalid" ), "diagnostic" );
        Files.writeString( source.resolve( "Dedicated.db" ), "new-world" );
        // WHEN
        underTest.create( world, "second", () -> true );
        // THEN
        assertThat( backups.resolve( "snapshot-latest/Dedicated.db" ) ).hasContent( "new-world" );
        assertThat( obsolete ).hasContent( "diagnostic" );
    }
}
