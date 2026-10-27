package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.UpdateState;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Restores the originals of an interrupted or failed transaction (AD-024).
 *
 * <p>Rollback is idempotent: it may itself be interrupted at any point and run again with the same result.
 * It walks the operations backwards and decides from the files actually present, not from the recorded
 * step, because a crash may have happened just before or just after any rename. Operations still
 * {@link OperationStep#PENDING} were never touched and are skipped.
 *
 * <pre>
 * directory: new content found in the installation -> moved back to staged
 *            original in the backup                -> moved back to the installation
 * file:      installation already holds the original digest -> nothing to do
 *            installation holds the new digest or nothing   -> durable copy of the backup renamed over it
 *            original did not exist                         -> the new file (new digest only) is deleted
 * anything else (unexpected digest, both or neither directory present) -> stop, keep everything
 * </pre>
 *
 * <p>The backup copy of an original is never removed here; it stays until a later transaction.
 */
@Log4j2
final class UpdateRollback {

    private final TransactionFiles files;

    private final Checkpoint checkpoint;

    /**
     * Creates the rollback.
     *
     * @param files
     *         file actions of the installation
     * @param checkpoint
     *         observation point after every file action
     */
    UpdateRollback( @Nonnull TransactionFiles files, @Nonnull Checkpoint checkpoint ) {
        this.files = requireNonNull( files, "files must not be null" );
        this.checkpoint = requireNonNull( checkpoint, "checkpoint must not be null" );
    }

    /**
     * Rolls a non-terminal transaction back and persists {@link UpdateState#ROLLED_BACK}.
     *
     * @param journal
     *         journal in {@code PREPARED}, {@code APPLYING} or {@code ROLLING_BACK}
     * @throws NullPointerException
     *         if the journal is null
     * @throws IllegalStateException
     *         if the transaction has already ended
     * @throws IOException
     *         if the files are ambiguous or an action fails; the journal stays non-terminal and recovery
     *         must run again or the installation needs manual repair
     */
    void rollback( @Nonnull UpdateJournal journal ) throws IOException {
        requireNonNull( journal, "journal must not be null" );
        if ( journal.getState().isTerminal() ) {
            throw new IllegalStateException( "A finished transaction cannot be rolled back" );
        }
        log.info( "Rolling back update {} from state {}", journal.getUpdateId(), journal.getState() );
        if ( journal.getState() != UpdateState.ROLLING_BACK ) {
            journal.changeState( UpdateState.ROLLING_BACK );
        }
        List< JournalOperation > operations = journal.getOperations();
        for ( int index = operations.size() - 1; index >= 0; index-- ) {
            JournalOperation operation = operations.get( index );
            if ( operation.step() == OperationStep.PENDING || operation.step() == OperationStep.RESTORED ) {
                continue;
            }
            journal.changeStep( operation.item(), OperationStep.RESTORE_INTENT );
            if ( operation.item().getKind() == DistributionItem.Kind.DIRECTORY ) {
                restoreDirectory( operation );
            } else {
                restoreFile( operation );
            }
            journal.changeStep( operation.item(), OperationStep.RESTORED );
        }
        journal.changeState( UpdateState.ROLLED_BACK );
        log.info( "Rolled back update {}", journal.getUpdateId() );
    }

    private void restoreDirectory( JournalOperation operation ) throws IOException {
        DistributionItem item = operation.item();
        Path installed = files.installed( item );
        Path staged = files.source( item );
        Path backup = files.backup( item );
        if ( !files.exists( staged ) ) {
            if ( !files.exists( installed ) ) {
                throw new IOException( "Neither the new nor the installed " + item + " exists" );
            }
            files.move( installed, staged, false );
            checkpoint.reached( item + ":new-removed" );
        }
        if ( operation.originalExists() ) {
            if ( files.exists( backup ) ) {
                if ( files.exists( installed ) ) {
                    throw new IOException( "Both the original and an unknown " + item + " exist" );
                }
                files.move( backup, installed, false );
                checkpoint.reached( item + ":original-restored" );
            } else if ( !files.exists( installed ) ) {
                throw new IOException( "The original " + item + " is missing" );
            }
        } else if ( files.exists( installed ) ) {
            throw new IOException( "An unexpected " + item + " exists in the installation" );
        }
        log.debug( "Restored directory {}", item );
    }

    private void restoreFile( JournalOperation operation ) throws IOException {
        DistributionItem item = operation.item();
        Path installed = files.installed( item );
        String current = files.digestOrNull( installed );
        if ( !operation.originalExists() ) {
            if ( current != null ) {
                requireNewContent( item, current, operation );
                Files.delete( installed );
                checkpoint.reached( item + ":new-removed" );
            }
            return;
        }
        if ( operation.originalSha256().equals( current ) ) {
            return;
        }
        if ( current != null ) {
            requireNewContent( item, current, operation );
        }
        Path backup = files.backup( item );
        if ( !operation.originalSha256().equals( files.digestOrNull( backup ) ) ) {
            throw new IOException( "The backup of " + item + " is missing or damaged" );
        }
        String restored = files.copyOver( backup, installed );
        if ( !restored.equals( operation.originalSha256() ) ) {
            throw new IOException( "The restored " + item + " does not match its original" );
        }
        checkpoint.reached( item + ":original-restored" );
        log.debug( "Restored file {}", item );
    }

    private static void requireNewContent( DistributionItem item, String current, JournalOperation operation )
            throws IOException {
        if ( !current.equals( operation.newSha256() ) ) {
            throw new IOException( "The installed " + item + " has an unexpected identity" );
        }
    }
}
