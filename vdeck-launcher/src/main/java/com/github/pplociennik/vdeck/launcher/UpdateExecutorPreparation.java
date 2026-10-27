package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.Objects.requireNonNull;

/**
 * Prepares the executor, an independent copy of the staged JRE and updater JAR in
 * {@code .vDeck/update/executor} (AD-024). The updater runs only from this copy, so it can replace the
 * installed {@code jre} and {@code lib} and the staged ones, and the start script can run recovery even
 * when the installation has no runtime left.
 *
 * <p>Files are copied, never linked; every copy is forced to the device and compared by digest with a
 * fresh read of its source. The copy is built in a temporary directory and renamed to {@code executor}
 * as the last step. The installation itself is never changed.
 */
@Log4j2
final class UpdateExecutorPreparation {

    /**
     * Copies and verifies the executor and publishes it.
     *
     * @param layout
     *         update layout with a published staged directory; {@code executor} must not exist
     * @param deadline
     *         shared preparation deadline
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if a copy fails or differs, a link is found or the deadline expires; nothing is published
     */
    void prepare( @Nonnull UpdateLayout layout, @Nonnull Deadline deadline ) throws IOException {
        requireNonNull( layout, "layout must not be null" );
        requireNonNull( deadline, "deadline must not be null" );
        Path temporary = layout.updateDirectory().resolve( ".tmp-executor-" + UUID.randomUUID() );
        boolean published = false;
        try {
            Files.createDirectory( temporary );
            FileWorldSnapshotStore.checkDirectory( layout.installation(), temporary );
            int files = copyTree( layout.installation(), layout.stagedDirectory().resolve( "jre" ),
                    temporary.resolve( "jre" ), deadline );
            copyVerified( layout.stagedDirectory().resolve( UpdateLayout.UPDATER_JAR ),
                    temporary.resolve( layout.executorUpdaterJar().getFileName() ) );
            deadline.check();
            Files.move( temporary, layout.executorDirectory(), ATOMIC_MOVE );
            published = true;
            log.info( "Prepared the update executor with {} runtime files", files );
        } finally {
            if ( !published ) {
                DurableFiles.deleteTree( layout.installation(), temporary );
            }
        }
    }

    private static int copyTree( Path installation, Path source, Path target, Deadline deadline ) throws IOException {
        FileWorldSnapshotStore.checkDirectory( installation, source );
        List< Path > entries;
        try ( Stream< Path > walk = Files.walk( source ) ) {
            entries = walk.toList();
        }
        int files = 0;
        for ( Path entry : entries ) {
            deadline.check();
            Path copy = target.resolve( source.relativize( entry ).toString() );
            BasicFileAttributes attributes = Files.readAttributes( entry, BasicFileAttributes.class, NOFOLLOW_LINKS );
            if ( attributes.isSymbolicLink() || attributes.isOther() ) {
                throw new IOException( "The staged runtime contains a link" );
            }
            if ( attributes.isDirectory() ) {
                Files.createDirectories( copy );
            } else {
                copyVerified( entry, copy );
                files++;
            }
        }
        return files;
    }

    private static void copyVerified( Path source, Path target ) throws IOException {
        String copied = DurableFiles.copyDurably( source, target );
        if ( !copied.equals( DurableFiles.sha256( source ) ) || !copied.equals( DurableFiles.sha256( target ) ) ) {
            throw new IOException( "The executor copy of " + source.getFileName() + " differs from its source" );
        }
    }
}
