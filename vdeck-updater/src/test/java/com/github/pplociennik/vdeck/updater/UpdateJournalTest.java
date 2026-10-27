package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
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
 * Verifies the durable transaction journal and its validation as untrusted input.
 */
class UpdateJournalTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String DIGEST = "c".repeat( 64 );

    @TempDir
    private Path directory;

    private Path file;

    private List< String > checkpoints;

    private UpdateJournal underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a prepared journal with one directory and one file operation.
     */
    @BeforeEach
    void setUp() throws Exception {
        file = directory.resolve( "apply.journal" );
        checkpoints = new ArrayList<>();
        underTest = UpdateJournal.create( file, "1.0.0", "1.1.0", List.of(
                new JournalOperation( DistributionItem.LIB, true, null, null, OperationStep.PENDING ),
                new JournalOperation( DistributionItem.README_MD, false, null, DIGEST, OperationStep.PENDING ) ),
                checkpoints::add );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a prepared journal is persisted and read back with every operation.
     */
    @Test
    void shouldPersistPreparedJournal_whenCreated() throws Exception {
        // GIVEN
        // The fixture journal is prepared.

        // WHEN
        UpdateJournal read = UpdateJournal.read( file, Checkpoint.NONE );

        // THEN
        assertThat( read.getState() ).isEqualTo( UpdateState.PREPARED );
        assertThat( read.getUpdateId() ).isEqualTo( underTest.getUpdateId() );
        assertThat( read.getFromVersion() ).isEqualTo( "1.0.0" );
        assertThat( read.getToVersion() ).isEqualTo( "1.1.0" );
        assertThat( read.getOperations() ).isEqualTo( underTest.getOperations() );
        assertThat( checkpoints ).containsExactly( "journal:PREPARED" );
    }

    /**
     * Verifies that state and step changes are durable before the method returns.
     */
    @Test
    void shouldPersistChanges_whenStateAndStepChange() throws Exception {
        // GIVEN
        // The fixture journal is prepared.

        // WHEN
        underTest.changeState( UpdateState.APPLYING );
        underTest.changeStep( DistributionItem.LIB, OperationStep.BACKUP_INTENT );

        // THEN
        UpdateJournal read = UpdateJournal.read( file, Checkpoint.NONE );
        assertThat( read.getState() ).isEqualTo( UpdateState.APPLYING );
        assertThat( read.getOperations().getFirst().step() ).isEqualTo( OperationStep.BACKUP_INTENT );
        assertThat( checkpoints ).containsExactly( "journal:PREPARED", "journal:APPLYING", "LIB:BACKUP_INTENT" );
    }

    /**
     * Verifies that a step of an item outside the transaction is rejected.
     */
    @Test
    void shouldThrowException_whenItemIsNotPartOfTransaction() {
        // GIVEN
        // The journal has no operation for the manual.

        // WHEN
        var failure = catchThrowable( () -> underTest.changeStep( DistributionItem.MANUAL, OperationStep.APPLIED ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that a journal naming an item outside the closed list is not trusted.
     */
    @Test
    void shouldThrowException_whenJournalNamesUnknownItem() throws Exception {
        // GIVEN
        Files.writeString( file, Files.readString( file ).replace( "op.0.item=LIB", "op.0.item=DATABASE" ) );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournal.read( file, Checkpoint.NONE ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "Malformed" );
    }

    /**
     * Verifies that a journal with an implausible operation count is not trusted.
     */
    @Test
    void shouldThrowException_whenOperationCountIsInvalid() throws Exception {
        // GIVEN
        Files.writeString( file, Files.readString( file ).replace( "operations=2", "operations=99" ) );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournal.read( file, Checkpoint.NONE ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that an injected version is rejected.
     */
    @Test
    void shouldThrowException_whenVersionIsMalformed() {
        // GIVEN
        List< JournalOperation > operations = List.of(
                new JournalOperation( DistributionItem.LIB, true, null, null, OperationStep.PENDING ) );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournal.create( directory.resolve( "other.journal" ),
                "1.0.0\nstate=COMMITTED", "1.1.0", operations, Checkpoint.NONE ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that duplicate operations for one item are rejected.
     */
    @Test
    void shouldThrowException_whenOperationsRepeatItem() {
        // GIVEN
        JournalOperation lib = new JournalOperation( DistributionItem.LIB, true, null, null, OperationStep.PENDING );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournal.create( directory.resolve( "other.journal" ), "1.0.0",
                "1.1.0", List.of( lib, lib ), Checkpoint.NONE ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that a failed write leaves the in-memory state unchanged.
     */
    @Test
    void shouldKeepPreviousState_whenPersistenceFails() throws Exception {
        // GIVEN
        Files.delete( file );
        Files.createDirectory( file );

        // WHEN
        var failure = catchThrowable( () -> underTest.changeState( UpdateState.APPLYING ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( underTest.getState() ).isEqualTo( UpdateState.PREPARED );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingState = catchThrowable( () -> underTest.changeState( null ) );
        var missingFile = catchThrowable( () -> UpdateJournal.read( null, Checkpoint.NONE ) );

        // THEN
        assertThat( missingState ).isInstanceOf( NullPointerException.class );
        assertThat( missingFile ).isInstanceOf( NullPointerException.class );
    }
}
