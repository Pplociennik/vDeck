package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Applies the operations of a {@link UpdateState#PREPARED} journal (AD-024).
 *
 * <pre>
 * directory: BACKUP_INTENT, rename installed -> backup, BACKED_UP,
 *            INSTALL_INTENT, rename staged -> installed, APPLIED
 * file:      BACKUP_INTENT, durable copy installed -> backup, BACKED_UP,
 *            INSTALL_INTENT, atomic rename new -> installed (replacing), APPLIED
 * </pre>
 *
 * <p>A file, including the running start script, is never absent: its original is copied, not moved,
 * and the new content replaces it in one atomic rename. Every intent is durable before its action.
 * Failures propagate to the caller, which rolls back with {@link UpdateRollback}; the executor is never
 * touched.
 */
@Log4j2
final class FileReplacer {

    private final TransactionFiles files;

    private final Checkpoint checkpoint;

    /**
     * Creates the replacer.
     *
     * @param files
     *         file actions of the installation
     * @param checkpoint
     *         observation point after every file action
     */
    FileReplacer( @Nonnull TransactionFiles files, @Nonnull Checkpoint checkpoint ) {
        this.files = requireNonNull( files, "files must not be null" );
        this.checkpoint = requireNonNull( checkpoint, "checkpoint must not be null" );
    }

    /**
     * Moves the journal to {@link UpdateState#APPLYING} and applies every operation in order.
     *
     * @param journal
     *         prepared journal
     * @throws NullPointerException
     *         if the journal is null
     * @throws IllegalStateException
     *         if the journal is not prepared
     * @throws IOException
     *         if a journal write or file action fails; the installation is then partially updated and
     *         must be rolled back
     */
    void apply( @Nonnull UpdateJournal journal ) throws IOException {
        requireNonNull( journal, "journal must not be null" );
        if ( journal.getState() != UpdateState.PREPARED ) {
            throw new IllegalStateException( "Only a prepared transaction can be applied" );
        }
        journal.changeState( UpdateState.APPLYING );
        for ( JournalOperation operation : journal.getOperations() ) {
            if ( operation.item().getKind() == DistributionItem.Kind.DIRECTORY ) {
                applyDirectory( journal, operation );
            } else {
                applyFile( journal, operation );
            }
        }
        log.info( "Applied {} operations of update {}", journal.getOperations().size(), journal.getUpdateId() );
    }

    private void applyDirectory( UpdateJournal journal, JournalOperation operation ) throws IOException {
        DistributionItem item = operation.item();
        Path installed = files.installed( item );
        journal.changeStep( item, OperationStep.BACKUP_INTENT );
        if ( operation.originalExists() ) {
            files.move( installed, files.backup( item ), false );
            checkpoint.reached( item + ":backup-moved" );
        }
        journal.changeStep( item, OperationStep.BACKED_UP );
        journal.changeStep( item, OperationStep.INSTALL_INTENT );
        files.move( files.source( item ), installed, false );
        checkpoint.reached( item + ":installed" );
        journal.changeStep( item, OperationStep.APPLIED );
    }

    private void applyFile( UpdateJournal journal, JournalOperation operation ) throws IOException {
        DistributionItem item = operation.item();
        Path installed = files.installed( item );
        journal.changeStep( item, OperationStep.BACKUP_INTENT );
        if ( operation.originalExists() ) {
            String copied = files.copyOver( installed, files.backup( item ) );
            if ( !copied.equals( operation.originalSha256() ) ) {
                throw new IOException( "The original of " + item + " changed during the update" );
            }
            checkpoint.reached( item + ":backup-copied" );
        }
        journal.changeStep( item, OperationStep.BACKED_UP );
        journal.changeStep( item, OperationStep.INSTALL_INTENT );
        files.move( files.source( item ), installed, true );
        checkpoint.reached( item + ":installed" );
        journal.changeStep( item, OperationStep.APPLIED );
    }
}
