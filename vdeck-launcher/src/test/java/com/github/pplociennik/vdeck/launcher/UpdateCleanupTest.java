package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.UpdateJournalHeader;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the recognition and removal of update leftovers.
 */
class UpdateCleanupTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final UUID UPDATE_ID = UUID.fromString( "11111111-2222-3333-4444-555555555555" );

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private UpdateCleanup underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation with every kind of update leftover.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        for ( Path work : new Path[] { layout.stagedDirectory(), layout.executorDirectory(), layout.mergedDirectory(),
                layout.downloadDirectory(), layout.backupDirectory(),
                layout.updateDirectory().resolve( ".tmp-staged-x" ) } ) {
            Files.createDirectories( work );
            Files.writeString( work.resolve( "file" ), "x" );
        }
        underTest = new UpdateCleanup( layout );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the classification of a missing, terminal, active and unreadable journal.
     */
    @Test
    void shouldClassifyJournal_whenInspecting() throws Exception {
        // GIVEN
        // The journal is written step by step.

        // WHEN
        var none = underTest.inspect();
        journal( UpdateState.COMMITTED );
        var terminal = underTest.inspect();
        journal( UpdateState.APPLYING );
        var active = underTest.inspect();
        Files.writeString( layout.journal(), "garbage" );
        var unreadable = underTest.inspect();

        // THEN
        assertThat( none ).isEqualTo( UpdateCleanup.JournalStatus.NONE );
        assertThat( terminal ).isEqualTo( UpdateCleanup.JournalStatus.TERMINAL );
        assertThat( active ).isEqualTo( UpdateCleanup.JournalStatus.ACTIVE );
        assertThat( unreadable ).isEqualTo( UpdateCleanup.JournalStatus.ACTIVE );
    }

    /**
     * Verifies that a finished journal is archived into the backup before the work files are deleted.
     */
    @Test
    void shouldArchiveJournalAndDeleteWorkFiles_whenTransactionEnded() throws Exception {
        // GIVEN
        journal( UpdateState.ROLLED_BACK );

        // WHEN
        underTest.finishTerminal();

        // THEN
        assertThat( layout.journal() ).doesNotExist();
        assertThat( layout.backupDirectory().resolve( "apply-" + UPDATE_ID + ".journal" ) ).exists();
        assertThat( layout.backupDirectory().resolve( "file" ) ).exists();
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).containsExactly( layout.backupDirectory() );
        }
    }

    /**
     * Verifies that an active transaction keeps every file needed for recovery.
     */
    @Test
    void shouldKeepEverything_whenTransactionIsActive() throws Exception {
        // GIVEN
        journal( UpdateState.APPLYING );

        // WHEN
        underTest.finishTerminal();
        underTest.discardUnpublished();

        // THEN
        assertThat( layout.journal() ).exists();
        assertThat( layout.executorDirectory() ).exists();
        assertThat( layout.stagedDirectory() ).exists();
    }

    /**
     * Verifies that without a journal every unpublished work file goes, while the backup stays.
     */
    @Test
    void shouldDeleteUnpublishedFiles_whenNoJournalExists() throws Exception {
        // GIVEN
        // No journal exists.

        // WHEN
        underTest.discardUnpublished();

        // THEN
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).containsExactly( layout.backupDirectory() );
        }
    }

    /**
     * Verifies that the layout is required.
     */
    @Test
    void shouldThrowException_whenLayoutIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> new UpdateCleanup( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void journal( UpdateState state ) throws Exception {
        Properties values = new Properties();
        new UpdateJournalHeader( UPDATE_ID, state ).writeTo( values );
        try ( var output = Files.newOutputStream( layout.journal() ) ) {
            values.store( output, null );
        }
    }
}
