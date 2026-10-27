package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/**
 * Exclusive operating-system lock on one of the installation's coordination files in {@code .vDeck}.
 *
 * <p>{@link Kind#INSTALLATION} ({@code vdeck.lock}) is owned by whichever process may change the
 * installation: the panel while it runs, or the launcher and updater between panel runs.
 * {@link Kind#STARTUP_GATE} ({@code startup.lock}) serializes launcher and updater starts; a launcher keeps
 * it while its panel runs, so a second start can neither clean up nor replace files (AD-024).
 *
 * <p>The lock is released by {@link #close()} or by the operating system when the process exits. It is
 * never waited for: callers decide whether a busy lock means "exit" or "retry until a deadline".
 */
@Log4j2
public final class InstallationLock implements AutoCloseable {

    /**
     * Canonical installation root whose file this lock protects.
     */
    @Getter
    private final Path installation;

    /**
     * Coordination file held by this lock.
     */
    @Getter
    private final Kind kind;

    private final FileChannel channel;

    private final FileLock lock;

    private InstallationLock( Path installation, Kind kind, FileChannel channel, FileLock lock ) {
        this.installation = installation;
        this.kind = kind;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Tries once to acquire a coordination lock without waiting. Creates {@code .vDeck} and the lock file
     * when they are missing, after checking that no directory on the way is redirected.
     *
     * @param installation
     *         absolute installation root; it is resolved to its real path
     * @param kind
     *         coordination file to lock
     * @return the held lock, or an empty optional when another process or this JVM already holds it
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the installation is relative
     * @throws UncheckedIOException
     *         if the directory is unsafe or the lock file cannot be opened
     */
    public static @Nonnull Optional< InstallationLock > tryAcquire( @Nonnull Path installation, @Nonnull Kind kind ) {
        requireNonNull( installation, "installation must not be null" );
        requireNonNull( kind, "kind must not be null" );
        if ( !installation.isAbsolute() ) {
            throw new IllegalArgumentException( "installation must be absolute" );
        }
        try {
            Path root = installation.toRealPath();
            Path directory = root.resolve( UpdateLayout.PANEL_DIRECTORY );
            FileWorldSnapshotStore.checkDirectory( root, root );
            Files.createDirectories( directory );
            FileWorldSnapshotStore.checkDirectory( root, directory );
            FileChannel channel = FileChannel.open( directory.resolve( kind.fileName ), CREATE, WRITE, NOFOLLOW_LINKS );
            FileLock lock = tryLock( channel );
            if ( lock == null ) {
                channel.close();
                log.debug( "Lock {} of installation {} is held by another owner", kind, root );
                return Optional.empty();
            }
            log.debug( "Acquired lock {} of installation {}", kind, root );
            return Optional.of( new InstallationLock( root, kind, channel, lock ) );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not open the installation lock " + kind, exception );
        }
    }

    /**
     * Acquires a coordination lock or fails immediately when it is held.
     *
     * @param installation
     *         absolute installation root
     * @param kind
     *         coordination file to lock
     * @return the held lock
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalStateException
     *         if another owner holds the lock
     * @throws UncheckedIOException
     *         if the lock file cannot be opened safely
     */
    public static @Nonnull InstallationLock acquire( @Nonnull Path installation, @Nonnull Kind kind ) {
        return tryAcquire( installation, kind ).orElseThrow(
                () -> new IllegalStateException( "Another process owns the installation lock " + kind ) );
    }

    /**
     * Reports whether this lock is still held by this process.
     *
     * @return {@code true} until the lock is closed
     */
    public boolean isHeld() {
        return lock.isValid();
    }

    /** Releases the lock; closing twice is harmless. */
    @Override
    public void close() {
        try {
            boolean held = lock.isValid();
            if ( held ) {
                lock.release();
            }
            channel.close();
            if ( held ) {
                log.debug( "Released lock {} of installation {}", kind, installation );
            }
        } catch ( IOException exception ) {
            log.warn( "Could not release installation lock {}", kind, exception );
        }
    }

    private static FileLock tryLock( FileChannel channel ) throws IOException {
        try {
            return channel.tryLock();
        } catch ( OverlappingFileLockException exception ) {
            // The same JVM already owns the region; for a second owner this is the same as a busy lock.
            return null;
        } catch ( IOException | RuntimeException exception ) {
            channel.close();
            throw exception;
        }
    }

    /**
     * Coordination files below {@code .vDeck}.
     */
    public enum Kind {

        /**
         * {@code vdeck.lock}: ownership of the installation's files and server.
         */
        INSTALLATION( "vdeck.lock" ),

        /**
         * {@code startup.lock}: serializes launcher and updater starts.
         */
        STARTUP_GATE( "startup.lock" );

        private final String fileName;

        Kind( String fileName ) {
            this.fileName = fileName;
        }
    }
}
