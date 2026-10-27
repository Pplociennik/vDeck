package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateJournalHeader;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Recognizes update leftovers and removes those that no recovery can need (AD-024). Must be called only
 * by the owner of both installation locks, after every predecessor that used the executor has exited.
 *
 * <p>The journal is the only signal of an active transaction: while it exists and is not terminal,
 * nothing is deleted and the launcher hands over to recovery. A terminal journal is first moved into
 * the backup directory, which keeps the originals until the next update, and only then are the executor,
 * the staged package, merged settings and downloads deleted. Without a journal, every work directory is
 * an unpublished leftover. A failed deletion is logged and the leftover stays for the next start.
 */
@Log4j2
final class UpdateCleanup {

    private final UpdateLayout layout;

    /**
     * Creates the cleanup for one installation.
     *
     * @param layout
     *         update layout of the canonical installation
     */
    UpdateCleanup( @Nonnull UpdateLayout layout ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
    }

    /**
     * Classifies the transaction journal.
     *
     * @return {@link JournalStatus#NONE} without a journal, {@link JournalStatus#TERMINAL} for a finished
     *         transaction, {@link JournalStatus#ACTIVE} otherwise — including an unreadable journal, which
     *         only recovery may judge
     */
    @Nonnull
    JournalStatus inspect() {
        if ( !Files.exists( layout.journal(), NOFOLLOW_LINKS ) ) {
            return JournalStatus.NONE;
        }
        try {
            UpdateJournalHeader header = UpdateJournalHeader.read( layout.journal() );
            log.info( "Found update {} in state {}", header.updateId(), header.state() );
            return header.state().isTerminal() ? JournalStatus.TERMINAL : JournalStatus.ACTIVE;
        } catch ( IOException exception ) {
            log.warn( "The update journal is unreadable; recovery must decide", exception );
            return JournalStatus.ACTIVE;
        }
    }

    /**
     * Archives a terminal journal into the backup and deletes the remaining work directories.
     */
    void finishTerminal() {
        try {
            UpdateJournalHeader header = UpdateJournalHeader.read( layout.journal() );
            if ( !header.state().isTerminal() ) {
                log.warn( "Refusing to clean up the active update {}", header.updateId() );
                return;
            }
            FileWorldSnapshotStore.checkDirectory( layout.installation(), layout.updateDirectory() );
            Files.createDirectories( layout.backupDirectory() );
            FileWorldSnapshotStore.checkDirectory( layout.installation(), layout.backupDirectory() );
            Files.move( layout.journal(), layout.backupDirectory().resolve( "apply-" + header.updateId() + ".journal" ),
                    ATOMIC_MOVE, REPLACE_EXISTING );
            log.info( "Archived the journal of update {} ({})", header.updateId(), header.state() );
        } catch ( IOException | RuntimeException exception ) {
            log.warn( "Could not archive the finished update journal; leftovers are kept", exception );
            return;
        }
        discardUnpublished();
    }

    /**
     * Deletes work directories and temporary files when no journal exists. The backup is kept.
     */
    void discardUnpublished() {
        if ( Files.exists( layout.journal(), NOFOLLOW_LINKS ) ) {
            log.warn( "An update journal exists; work files are kept for recovery" );
            return;
        }
        if ( !Files.isDirectory( layout.updateDirectory(), NOFOLLOW_LINKS ) ) {
            return;
        }
        for ( Path directory : List.of( layout.executorDirectory(), layout.stagedDirectory(),
                layout.mergedDirectory(), layout.downloadDirectory() ) ) {
            delete( directory );
        }
        try ( DirectoryStream< Path > temporary = Files.newDirectoryStream( layout.updateDirectory(), ".tmp-*" ) ) {
            for ( Path leftover : temporary ) {
                delete( leftover );
            }
        } catch ( IOException exception ) {
            log.warn( "Could not list temporary update files", exception );
        }
    }

    private void delete( Path path ) {
        if ( !Files.exists( path, NOFOLLOW_LINKS ) ) {
            return;
        }
        try {
            if ( Files.isDirectory( path, NOFOLLOW_LINKS ) ) {
                DurableFiles.deleteTree( layout.installation(), path );
            } else {
                Files.deleteIfExists( path );
            }
            log.debug( "Removed update leftover {}", path.getFileName() );
        } catch ( IOException exception ) {
            log.warn( "Could not remove update leftover {}", path.getFileName(), exception );
        }
    }

    /**
     * Classification of the transaction journal.
     */
    enum JournalStatus {

        /**
         * No journal exists.
         */
        NONE,

        /**
         * A transaction needs recovery.
         */
        ACTIVE,

        /**
         * The transaction ended; its leftovers may be cleaned up.
         */
        TERMINAL
    }
}
