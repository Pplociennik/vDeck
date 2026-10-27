package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the idempotent rollback, including an interruption of the rollback itself at every point.
 */
class UpdateRollbackTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private DistributionFixture.Transaction transaction;

    private UpdateRollback underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Prepares and completely applies a transaction without committing it.
     */
    @BeforeEach
    void setUp() throws Exception {
        transaction = applied( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ),
                Checkpoint.NONE );
        underTest = new UpdateRollback( transaction.files(), Checkpoint.NONE );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rollback of a fully applied transaction restores the original installation exactly.
     */
    @Test
    void shouldRestoreOriginal_whenTransactionWasApplied() throws Exception {
        // GIVEN
        // The fixture transaction is applied.

        // WHEN
        underTest.rollback( transaction.journal() );

        // THEN
        assertThat( DistributionFixture.snapshot( transaction.layout().installation() ) )
                .isEqualTo( transaction.original() );
        assertThat( transaction.journal().getState() ).isEqualTo( UpdateState.ROLLED_BACK );
        assertThat( transaction.journal().getOperations() )
                .allMatch( operation -> operation.step() == OperationStep.RESTORED );
    }

    /**
     * Verifies that a rollback interrupted at any point and run again from the journal on disk restores the
     * original installation.
     */
    @Test
    void shouldRestoreOriginal_whenRollbackIsInterruptedAtAnyPoint() throws Exception {
        // GIVEN
        List< String > points = new ArrayList<>();
        underTest = new UpdateRollback( transaction.files(), points::add );
        underTest.rollback( UpdateJournal.read( transaction.layout().journal(), points::add ) );
        int total = points.size();

        // WHEN
        int restored = 0;
        for ( int crashAt = 1; crashAt <= total; crashAt++ ) {
            restored += interruptAndRepeat( crashAt ) ? 1 : 0;
        }

        // THEN
        assertThat( total ).isGreaterThan( 20 );
        assertThat( restored ).isEqualTo( total );
    }

    /**
     * Verifies that a file with an unexpected identity is never overwritten or deleted.
     */
    @Test
    void shouldStop_whenInstalledFileHasUnexpectedIdentity() throws Exception {
        // GIVEN
        Path readme = transaction.layout().resolve( "README.md" );
        Files.writeString( readme, "edited by hand" );

        // WHEN
        var failure = catchThrowable( () -> underTest.rollback( transaction.journal() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "unexpected identity" );
        assertThat( readme ).hasContent( "edited by hand" );
        assertThat( transaction.journal().getState() ).isEqualTo( UpdateState.ROLLING_BACK );
    }

    /**
     * Verifies that a missing original directory stops the rollback instead of guessing.
     */
    @Test
    void shouldStop_whenOriginalDirectoryIsMissing() throws Exception {
        // GIVEN
        DurableFiles.deleteTree( transaction.layout().installation(),
                transaction.layout().backupDirectory().resolve( "lib" ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.rollback( transaction.journal() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "original LIB" );
    }

    /**
     * Verifies that a damaged backup of a file stops the rollback.
     */
    @Test
    void shouldStop_whenFileBackupIsDamaged() throws Exception {
        // GIVEN
        Files.writeString( transaction.layout().backupDirectory().resolve( "README.txt" ), "damaged" );

        // WHEN
        var failure = catchThrowable( () -> underTest.rollback( transaction.journal() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "backup" );
    }

    /**
     * Verifies that a finished transaction is never rolled back.
     */
    @Test
    void shouldThrowException_whenTransactionHasEnded() throws Exception {
        // GIVEN
        transaction.journal().changeState( UpdateState.COMMITTED );

        // WHEN
        var failure = catchThrowable( () -> underTest.rollback( transaction.journal() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class );
    }

    /**
     * Verifies that a null journal is rejected.
     */
    @Test
    void shouldThrowException_whenJournalIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.rollback( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static DistributionFixture.Transaction applied( Path installation, Checkpoint checkpoint ) throws Exception {
        DistributionFixture.Transaction prepared = DistributionFixture.prepareTransaction( installation, checkpoint );
        new FileReplacer( prepared.files(), Checkpoint.NONE ).apply( prepared.journal() );
        return prepared;
    }

    /**
     * Interrupts a rollback at the given checkpoint, then repeats it from the journal on disk.
     *
     * @return {@code true} when the interruption happened and the repeated rollback restored everything
     */
    private boolean interruptAndRepeat( int crashAt ) throws Exception {
        Path installation = Files.createDirectory( directory.toRealPath().resolve( "crash-" + crashAt ) );
        DistributionFixture.Transaction crashed = applied( installation, Checkpoint.NONE );
        int[] reached = { 0 };
        Checkpoint crashing = name -> {
            if ( ++reached[ 0 ] == crashAt ) {
                throw new IllegalStateException( "crash at " + name );
            }
        };
        UpdateJournal journal = UpdateJournal.read( crashed.layout().journal(), crashing );
        Throwable failure = catchThrowable( () -> new UpdateRollback( crashed.files(), crashing ).rollback( journal ) );
        if ( failure == null ) {
            return false;
        }
        UpdateJournal recovered = UpdateJournal.read( crashed.layout().journal(), Checkpoint.NONE );
        // A crash right after ROLLED_BACK was persisted leaves a finished transaction, as recovery sees it.
        if ( !recovered.getState().isTerminal() ) {
            new UpdateRollback( crashed.files(), Checkpoint.NONE ).rollback( recovered );
        }
        assertThat( DistributionFixture.snapshot( installation ) ).as( failure.getMessage() )
                .isEqualTo( crashed.original() );
        return true;
    }
}
