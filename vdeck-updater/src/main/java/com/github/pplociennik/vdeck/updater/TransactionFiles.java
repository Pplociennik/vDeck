package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * File-system actions shared by {@link FileReplacer} and {@link UpdateRollback}. Every path is derived
 * from the closed {@link DistributionItem} list and checked again right before use; every move is an
 * atomic rename on the installation's volume.
 *
 * <p>Windows briefly denies renames of files opened by scanners or indexers, so a denied rename is
 * retried a few times before the transaction fails.
 */
@Log4j2
final class TransactionFiles {

    private static final int RENAME_ATTEMPTS = 10;

    private static final long RENAME_RETRY_MILLIS = 300;

    private final UpdateLayout layout;

    /**
     * Creates the helper for one installation.
     *
     * @param layout
     *         update layout of the canonical installation
     */
    TransactionFiles( @Nonnull UpdateLayout layout ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
    }

    /**
     * Returns the installed location of an item.
     *
     * @param item
     *         distribution item
     * @return absolute path inside the installation
     */
    @Nonnull
    Path installed( @Nonnull DistributionItem item ) {
        return layout.resolve( item.getPath() );
    }

    /**
     * Returns the location of the new content of an item: the staged package, or the merged settings.
     *
     * @param item
     *         distribution item
     * @return absolute source path inside {@code .vDeck/update}
     */
    @Nonnull
    Path source( @Nonnull DistributionItem item ) {
        Path root = item.getKind() == DistributionItem.Kind.SETTINGS ? layout.mergedDirectory()
                : layout.stagedDirectory();
        return root.resolve( item.getPath() );
    }

    /**
     * Returns the backup location of an item's original.
     *
     * @param item
     *         distribution item
     * @return absolute path inside {@code .vDeck/update/backup}
     */
    @Nonnull
    Path backup( @Nonnull DistributionItem item ) {
        return layout.backupDirectory().resolve( item.getPath() );
    }

    /**
     * Atomically renames a file or directory, creating the target's parent directories after checking
     * that none of them is redirected.
     *
     * @param source
     *         existing file or directory
     * @param target
     *         new location
     * @param replace
     *         whether an existing target file is replaced in the same atomic step; without it an
     *         existing target fails the move
     * @throws IOException
     *         if a directory is unsafe or the rename keeps failing
     */
    void move( @Nonnull Path source, @Nonnull Path target, boolean replace ) throws IOException {
        prepareParent( target );
        FileWorldSnapshotStore.checkDirectory( layout.installation(), source.getParent() );
        // On Windows an atomic move replaces an existing file even without REPLACE_EXISTING.
        if ( !replace && Files.exists( target, NOFOLLOW_LINKS ) ) {
            throw new FileAlreadyExistsException( relative( target ).toString() );
        }
        for ( int attempt = 1; ; attempt++ ) {
            try {
                if ( replace ) {
                    Files.move( source, target, ATOMIC_MOVE, REPLACE_EXISTING );
                } else {
                    Files.move( source, target, ATOMIC_MOVE );
                }
                log.debug( "Moved {} to {}", relative( source ), relative( target ) );
                return;
            } catch ( AccessDeniedException exception ) {
                if ( attempt == RENAME_ATTEMPTS ) {
                    throw exception;
                }
                log.debug( "Rename of {} denied, retrying", relative( source ) );
                pause();
            } catch ( FileSystemException exception ) {
                if ( exception instanceof NoSuchFileException || attempt == RENAME_ATTEMPTS ) {
                    throw exception;
                }
                log.debug( "Rename of {} failed with {}, retrying", relative( source ), exception.getReason() );
                pause();
            }
        }
    }

    /**
     * Durably copies a file next to its target and then renames it over the target, so the target is
     * never absent and never partially written.
     *
     * @param source
     *         regular file to copy
     * @param target
     *         file to create or replace
     * @return SHA-256 of the copied content
     * @throws IOException
     *         if copying or renaming fails
     */
    @Nonnull
    String copyOver( @Nonnull Path source, @Nonnull Path target ) throws IOException {
        prepareParent( target );
        Path temporary = target.resolveSibling( ".vdeck-" + UUID.randomUUID() + ".tmp" );
        try {
            String digest = DurableFiles.copyDurably( source, temporary );
            move( temporary, target, true );
            return digest;
        } finally {
            Files.deleteIfExists( temporary );
        }
    }

    /**
     * Returns the digest of a regular file, or {@code null} when nothing exists at the path.
     *
     * @param file
     *         file to hash
     * @return lowercase hexadecimal SHA-256, or {@code null} for a missing file
     * @throws IOException
     *         if the path exists but is not a readable regular file
     */
    @Nullable
    String digestOrNull( @Nonnull Path file ) throws IOException {
        if ( !Files.exists( file, NOFOLLOW_LINKS ) ) {
            return null;
        }
        return DurableFiles.sha256( file );
    }

    /**
     * Reports whether a path exists without following links.
     *
     * @param path
     *         path to check
     * @return {@code true} when anything exists at the path
     */
    boolean exists( @Nonnull Path path ) {
        return Files.exists( path, NOFOLLOW_LINKS );
    }

    /**
     * Returns a path relative to the installation, for logs.
     *
     * @param path
     *         absolute path
     * @return installation-relative path when possible
     */
    @Nonnull
    Path relative( @Nonnull Path path ) {
        return path.startsWith( layout.installation() ) ? layout.installation().relativize( path ) : path;
    }

    private void prepareParent( Path target ) throws IOException {
        Path parent = target.getParent();
        Path existing = parent;
        while ( !Files.exists( existing, NOFOLLOW_LINKS ) ) {
            existing = existing.getParent();
        }
        FileWorldSnapshotStore.checkDirectory( layout.installation(), existing );
        Files.createDirectories( parent );
        FileWorldSnapshotStore.checkDirectory( layout.installation(), parent );
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep( RENAME_RETRY_MILLIS );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new IOException( "Interrupted while retrying a rename", exception );
        }
    }
}
