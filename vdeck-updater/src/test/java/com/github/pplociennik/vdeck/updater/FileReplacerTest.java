package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the journaled replacement of a complete installation, including an interruption after every
 * single durable write and file action followed by recovery from the files left on disk.
 */
class FileReplacerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private DistributionFixture.Transaction transaction;

    private List< String > points;

    private FileReplacer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Prepares an installation of 1.0.0 with a staged 1.1.0 and a replacer recording its checkpoints.
     */
    @BeforeEach
    void setUp() throws Exception {
        transaction = DistributionFixture.prepareTransaction(
                Files.createDirectory( directory.toRealPath().resolve( "installation" ) ), Checkpoint.NONE );
        points = new ArrayList<>();
        underTest = new FileReplacer( transaction.files(), points::add );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a completed apply installs the new version, merges settings, keeps user data and saves
     * every original in the backup.
     */
    @Test
    void shouldInstallNewVersion_whenAllOperationsSucceed() throws Exception {
        // GIVEN
        Path installation = transaction.layout().installation();

        // WHEN
        underTest.apply( transaction.journal() );

        // THEN
        assertThat( transaction.journal().getOperations() )
                .allMatch( operation -> operation.step() == OperationStep.APPLIED );
        assertThat( installation.resolve( "README.md" ) ).hasContent( "README.md of 1.1.0" );
        assertThat( installation.resolve( "jre/lib/modules" ) ).hasContent( "modules of 1.1.0" );
        assertThat( installation.resolve( ".vDeck/config/vDeck.properties" ) ).hasContent( "kept=custom\nadded=new\n" );
        assertThat( installation.resolve( "vdeck.db" ) ).hasContent( "user database" );
        assertThat( transaction.layout().backupDirectory().resolve( "jre/lib/modules" ) ).hasContent( "modules of 1.0.0" );
        assertThat( transaction.layout().backupDirectory().resolve( "start-vDeck.bat" ) )
                .hasContent( "start-vDeck.bat of 1.0.0" );
        assertThat( transaction.layout().stagedDirectory().resolve( "lib" ) ).doesNotExist();
    }

    /**
     * Verifies that an interruption at any point of the apply is fully recovered by a rollback that reads
     * the journal back from disk, as recovery after a crash does.
     */
    @Test
    void shouldRestoreOriginal_whenInterruptedAtAnyPoint() throws Exception {
        // GIVEN
        int total = countCheckpoints();

        // WHEN
        List< Integer > restored = new ArrayList<>();
        for ( int crashAt = 2; crashAt <= total; crashAt++ ) {
            if ( interruptAndRecover( crashAt ) ) {
                restored.add( crashAt );
            }
        }

        // THEN
        assertThat( total ).isGreaterThan( 40 );
        assertThat( restored ).hasSize( total - 1 );
    }

    /**
     * Verifies that an original file changed during the transaction stops the apply.
     */
    @Test
    void shouldFail_whenOriginalChangesAfterPlanning() throws Exception {
        // GIVEN
        Files.writeString( transaction.layout().resolve( "README.md" ), "edited meanwhile" );

        // WHEN
        var failure = catchThrowable( () -> underTest.apply( transaction.journal() ) );

        // THEN
        assertThat( failure ).hasMessageContaining( "changed during the update" );
    }

    /**
     * Verifies that only a prepared transaction can be applied.
     */
    @Test
    void shouldThrowException_whenJournalIsNotPrepared() throws Exception {
        // GIVEN
        transaction.journal().changeState( UpdateState.COMMITTED );

        // WHEN
        var failure = catchThrowable( () -> underTest.apply( transaction.journal() ) );

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
        var failure = catchThrowable( () -> underTest.apply( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Counts every checkpoint of a complete transaction, from the prepared journal to the last operation.
     */
    private int countCheckpoints() throws Exception {
        Path installation = Files.createDirectory( directory.toRealPath().resolve( "dry-run" ) );
        List< String > all = new ArrayList<>();
        DistributionFixture.Transaction dryRun = DistributionFixture.prepareTransaction( installation, all::add );
        new FileReplacer( dryRun.files(), all::add ).apply( dryRun.journal() );
        return all.size();
    }

    /**
     * Applies a fresh transaction that crashes at the given checkpoint and recovers it from disk.
     *
     * @return {@code true} when the crash happened and the installation was restored exactly
     */
    private boolean interruptAndRecover( int crashAt ) throws Exception {
        Path installation = Files.createDirectory( directory.toRealPath().resolve( "crash-" + crashAt ) );
        int[] reached = { 0 };
        Checkpoint crashing = name -> {
            if ( ++reached[ 0 ] == crashAt ) {
                throw new SimulatedCrash( name );
            }
        };
        DistributionFixture.Transaction crashed = DistributionFixture.prepareTransaction( installation, crashing );
        Throwable failure = catchThrowable( () -> new FileReplacer( crashed.files(), crashing ).apply( crashed.journal() ) );
        if ( !( failure instanceof SimulatedCrash ) ) {
            return false;
        }
        UpdateJournal recovered = UpdateJournal.read( crashed.layout().journal(), Checkpoint.NONE );
        new UpdateRollback( crashed.files(), Checkpoint.NONE ).rollback( recovered );
        Map< String, String > after = DistributionFixture.snapshot( installation );
        assertThat( after ).as( "installation after a crash at %s", failure.getMessage() )
                .isEqualTo( crashed.original() );
        assertThat( UpdateJournal.read( crashed.layout().journal(), Checkpoint.NONE ).getState() )
                .isEqualTo( UpdateState.ROLLED_BACK );
        return true;
    }

    /**
     * Interruption thrown from a checkpoint.
     */
    private static final class SimulatedCrash extends RuntimeException {

        private SimulatedCrash( String point ) {
            super( point );
        }
    }

}
