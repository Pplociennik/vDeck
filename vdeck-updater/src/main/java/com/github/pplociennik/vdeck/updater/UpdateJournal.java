package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateJournalHeader;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Durable record of one update transaction in {@code .vDeck/update/apply.journal} (AD-024).
 *
 * <p>Every change is persisted before the caller continues: a complete temporary file is forced to the
 * device and atomically renamed over the journal. The journal holds the stable
 * {@link UpdateJournalHeader}, both versions and the ordered operations with relative paths from the
 * closed {@link DistributionItem} list, existence flags and digests — never setting values.
 *
 * <p>The journal is untrusted input when read back: sizes, protocol, item names, digests and states are
 * validated, and an invalid journal stops recovery instead of guessing. Instances are confined to the
 * updater's single thread.
 */
@Log4j2
@Getter
final class UpdateJournal {

    private static final Pattern VERSION = Pattern.compile( "[0-9A-Za-z.+-]{1,64}" );

    private static final String COMMENT = "vDeck update transaction; do not edit";

    /**
     * Journal file.
     */
    private final Path file;

    /**
     * Identifier of the transaction.
     */
    private final UUID updateId;

    /**
     * Version installed before the transaction.
     */
    private final String fromVersion;

    /**
     * Version installed by the transaction.
     */
    private final String toVersion;

    /**
     * Current transaction state.
     */
    private UpdateState state;

    /**
     * Operations in application order; an immutable list replaced on every change.
     */
    private List< JournalOperation > operations;

    @Getter( AccessLevel.NONE )
    private final Checkpoint checkpoint;

    private UpdateJournal( Path file, UUID updateId, String fromVersion, String toVersion, UpdateState state,
                           List< JournalOperation > operations, Checkpoint checkpoint ) {
        this.file = file;
        this.updateId = updateId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.state = state;
        this.operations = List.copyOf( operations );
        this.checkpoint = checkpoint;
    }

    /**
     * Creates and persists a {@link UpdateState#PREPARED} journal before anything in the installation
     * changes.
     *
     * @param file
     *         journal path; must not exist as an active journal
     * @param fromVersion
     *         installed version
     * @param toVersion
     *         version being installed
     * @param operations
     *         pending operations in application order
     * @param checkpoint
     *         observation point after the write
     * @return persisted journal
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if a version or the operation list is invalid
     * @throws IOException
     *         if the journal cannot be written durably
     */
    static @Nonnull UpdateJournal create( @Nonnull Path file, @Nonnull String fromVersion, @Nonnull String toVersion,
                                          @Nonnull List< JournalOperation > operations,
                                          @Nonnull Checkpoint checkpoint ) throws IOException {
        requireNonNull( file, "file must not be null" );
        requireNonNull( checkpoint, "checkpoint must not be null" );
        UpdateJournal journal = new UpdateJournal( file, UUID.randomUUID(), validVersion( fromVersion ),
                validVersion( toVersion ), UpdateState.PREPARED, validOperations( operations ), checkpoint );
        journal.persist( "journal:" + UpdateState.PREPARED );
        log.info( "Prepared update {} from {} to {} with {} operations", journal.updateId, fromVersion, toVersion,
                operations.size() );
        return journal;
    }

    /**
     * Reads and validates a journal left by a previous run.
     *
     * @param file
     *         journal path
     * @param checkpoint
     *         observation point after later writes
     * @return validated journal
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the journal is unreadable, of another protocol or malformed
     */
    static @Nonnull UpdateJournal read( @Nonnull Path file, @Nonnull Checkpoint checkpoint ) throws IOException {
        requireNonNull( file, "file must not be null" );
        requireNonNull( checkpoint, "checkpoint must not be null" );
        Properties values = DurableFiles.readProperties( file, UpdateJournalHeader.MAX_JOURNAL_BYTES );
        UpdateJournalHeader header = UpdateJournalHeader.parse( values );
        try {
            int count = Integer.parseInt( required( values, "operations" ) );
            if ( count < 1 || count > DistributionItem.values().length ) {
                throw new IOException( "Invalid number of journal operations" );
            }
            List< JournalOperation > operations = new ArrayList<>();
            for ( int index = 0; index < count; index++ ) {
                operations.add( readOperation( values, "op." + index + "." ) );
            }
            return new UpdateJournal( file, header.updateId(), validVersion( required( values, "fromVersion" ) ),
                    validVersion( required( values, "toVersion" ) ), header.state(), validOperations( operations ),
                    checkpoint );
        } catch ( IllegalArgumentException exception ) {
            throw new IOException( "Malformed update journal", exception );
        }
    }

    /**
     * Persists a new transaction state.
     *
     * @param next
     *         new state
     * @throws NullPointerException
     *         if the state is null
     * @throws IOException
     *         if the journal cannot be written durably; the in-memory state is then unchanged
     */
    void changeState( @Nonnull UpdateState next ) throws IOException {
        requireNonNull( next, "next must not be null" );
        UpdateState previous = state;
        state = next;
        try {
            persist( "journal:" + next );
        } catch ( IOException exception ) {
            state = previous;
            throw exception;
        }
        log.info( "Update {} is now {}", updateId, next );
    }

    /**
     * Persists a new step of one operation.
     *
     * @param item
     *         item of the operation
     * @param next
     *         new step
     * @return the updated operation
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the journal has no operation for the item
     * @throws IOException
     *         if the journal cannot be written durably; the in-memory state is then unchanged
     */
    @Nonnull
    JournalOperation changeStep( @Nonnull DistributionItem item, @Nonnull OperationStep next ) throws IOException {
        requireNonNull( item, "item must not be null" );
        requireNonNull( next, "next must not be null" );
        List< JournalOperation > previous = operations;
        List< JournalOperation > updated = new ArrayList<>( previous );
        int index = indexOf( item );
        JournalOperation operation = updated.get( index ).withStep( next );
        updated.set( index, operation );
        operations = List.copyOf( updated );
        try {
            persist( item + ":" + next );
        } catch ( IOException exception ) {
            operations = previous;
            throw exception;
        }
        log.debug( "Update {} operation {} is now {}", updateId, item, next );
        return operation;
    }

    private int indexOf( DistributionItem item ) {
        for ( int index = 0; index < operations.size(); index++ ) {
            if ( operations.get( index ).item() == item ) {
                return index;
            }
        }
        throw new IllegalArgumentException( "The journal has no operation for " + item );
    }

    private void persist( String point ) throws IOException {
        Properties values = new Properties();
        new UpdateJournalHeader( updateId, state ).writeTo( values );
        values.setProperty( "fromVersion", fromVersion );
        values.setProperty( "toVersion", toVersion );
        values.setProperty( "operations", Integer.toString( operations.size() ) );
        for ( int index = 0; index < operations.size(); index++ ) {
            writeOperation( values, "op." + index + ".", operations.get( index ) );
        }
        DurableFiles.writeProperties( file, values, COMMENT );
        checkpoint.reached( point );
    }

    private static void writeOperation( Properties values, String prefix, JournalOperation operation ) {
        values.setProperty( prefix + "item", operation.item().name() );
        values.setProperty( prefix + "originalExists", Boolean.toString( operation.originalExists() ) );
        if ( operation.originalSha256() != null ) {
            values.setProperty( prefix + "originalSha256", operation.originalSha256() );
        }
        if ( operation.newSha256() != null ) {
            values.setProperty( prefix + "newSha256", operation.newSha256() );
        }
        values.setProperty( prefix + "step", operation.step().name() );
    }

    private static JournalOperation readOperation( Properties values, String prefix ) throws IOException {
        String exists = required( values, prefix + "originalExists" );
        if ( !exists.equals( "true" ) && !exists.equals( "false" ) ) {
            throw new IOException( "Invalid existence flag in the update journal" );
        }
        return new JournalOperation( DistributionItem.valueOf( required( values, prefix + "item" ) ),
                Boolean.parseBoolean( exists ), values.getProperty( prefix + "originalSha256" ),
                values.getProperty( prefix + "newSha256" ), OperationStep.valueOf( required( values, prefix + "step" ) ) );
    }

    private static List< JournalOperation > validOperations( List< JournalOperation > operations ) {
        requireNonNull( operations, "operations must not be null" );
        if ( operations.isEmpty() ) {
            throw new IllegalArgumentException( "A transaction needs at least one operation" );
        }
        Set< DistributionItem > seen = EnumSet.noneOf( DistributionItem.class );
        for ( JournalOperation operation : operations ) {
            if ( !seen.add( requireNonNull( operation, "operation must not be null" ).item() ) ) {
                throw new IllegalArgumentException( "Duplicate journal operation " + operation.item() );
            }
        }
        return operations;
    }

    private static String validVersion( String version ) {
        requireNonNull( version, "version must not be null" );
        if ( !VERSION.matcher( version ).matches() ) {
            throw new IllegalArgumentException( "Invalid version in the update journal" );
        }
        return version;
    }

    private static String required( Properties values, String key ) throws IOException {
        String value = values.getProperty( key );
        if ( value == null ) {
            throw new IOException( "Missing update journal entry " + key );
        }
        return value;
    }
}
