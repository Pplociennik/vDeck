package com.github.pplociennik.vdeck.runtime.lifecycle;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/** Atomic, bounded, secret-free recovery token outside user-editable configuration. */
@Log4j2
public final class ManagedLaunchStore implements AutoCloseable {

    private final Path installation;

    private final Path token;

    private final FileChannel lockChannel;

    private final FileLock lock;

    /**
     * Creates a token store scoped to this installation.
     *
     * @param installation
     *         absolute installation root
     * @throws NullPointerException
     *         if the installation is null
     * @throws IllegalArgumentException
     *         if the installation is relative
     * @throws UncheckedIOException
     *         if canonicalization, directory validation, or lock acquisition fails
     */
    public ManagedLaunchStore( @Nonnull Path installation ) {
        requireNonNull( installation, "installation must not be null" );
        if ( !installation.isAbsolute() ) {
            throw new IllegalArgumentException( "Recovery root must be absolute" );
        }
        try {
            this.installation = installation.toRealPath();
            token = this.installation.resolve( ".vDeck/server.pid" );
            FileWorldSnapshotStore.checkDirectory( this.installation, this.installation );
            Files.createDirectories( token.getParent() );
            FileWorldSnapshotStore.checkDirectory( this.installation, token.getParent() );
            lockChannel = FileChannel.open( token.getParent().resolve( "vdeck.lock" ), CREATE, WRITE, NOFOLLOW_LINKS );
            try {
                lock = lockChannel.tryLock();
                if ( lock == null ) {
                    throw new IllegalStateException( "Another panel owns this installation" );
                }
            } catch ( IOException | RuntimeException exception ) {
                lockChannel.close();
                throw exception;
            }
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not acquire installation lock", exception );
        }
    }

    /** Releases the installation lock without terminating any server process. */
    @Override
    public void close() {
        try {
            if ( lock.isValid() ) {
                lock.release();
            }
            lockChannel.close();
        } catch ( IOException exception ) {
            log.warn( "Could not release installation lock", exception );
        }
    }

    /**
     * Returns the executable whose leftover processes must block a new launch.
     *
     * @return installation-bound executable path
     */
    public @Nonnull Path executable() {
        return installation.resolve( "valheim_server.exe" );
    }

    /**
     * Persists the captured world and process token without storing process arguments or secrets.
     *
     * @param launch
     *         launch whose identity has been verified by the runtime
     * @throws NullPointerException
     *         if the launch is null
     * @throws UncheckedIOException
     *         if persistence fails
     */
    void write( ManagedLaunch launch ) {
        requireNonNull( launch, "launch must not be null" );
        Path temporary = null;
        try {
            FileWorldSnapshotStore.checkDirectory( installation, installation );
            Files.createDirectories( token.getParent() );
            FileWorldSnapshotStore.checkDirectory( installation, token.getParent() );
            Properties values = new Properties();
            values.setProperty( "launch", launch.id() );
            values.setProperty( "pid", Long.toString( launch.identity().pid() ) );
            values.setProperty( "started", launch.identity().startedAt().toString() );
            values.setProperty( "executable", launch.identity().executable().toString() );
            values.setProperty( "world", launch.world().name() );
            values.setProperty( "saveDirectory", launch.world().saveDirectory().toString() );
            values.setProperty( "snapshotDirectory", launch.world().snapshotDirectory().toString() );
            values.setProperty( "log", launch.logFile().toString() );
            temporary = Files.createTempFile( token.getParent(), "server-", ".tmp" );
            try ( var output = Files.newOutputStream( temporary, WRITE, NOFOLLOW_LINKS ) ) {
                values.store( output, "vDeck managed launch" );
            }
            try ( var channel = FileChannel.open( temporary, WRITE, NOFOLLOW_LINKS ) ) {
                channel.force( true );
            }
            Files.move( temporary, token, REPLACE_EXISTING, ATOMIC_MOVE );
            temporary = null;
            log.debug( "Persisted recovery token for launch {}", launch.id() );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not persist managed launch", exception );
        } finally {
            if ( temporary != null ) {
                deleteTemporary( temporary );
            }
        }
    }

    /**
     * Reads and validates a saved launch within bounded input and authorized installation paths.
     *
     * @return saved context or an empty optional when no token exists
     * @throws UncheckedIOException
     *         if the file is unreadable or unsafe
     * @throws RuntimeException
     *         if its contents are invalid
     */
    Optional< ManagedLaunch > read() {
        if ( !Files.exists( token, NOFOLLOW_LINKS ) ) {
            return Optional.empty();
        }
        try {
            FileWorldSnapshotStore.checkDirectory( installation, token.getParent() );
            if ( Files.isSymbolicLink( token ) || !token.toRealPath().equals( token )
                    || Files.size( token ) > 16 * 1024 ) {
                throw new IOException( "Unsafe recovery token" );
            }
            Properties values = new Properties();
            try ( var input = Files.newInputStream( token, NOFOLLOW_LINKS ) ) {
                byte[] contents = input.readNBytes( 16 * 1024 + 1 );
                if ( contents.length > 16 * 1024 ) {
                    throw new IOException( "Recovery token size limit exceeded" );
                }
                values.load( new ByteArrayInputStream( contents ) );
            }
            Path executable = Path.of( required( values, "executable" ) ).normalize();
            Path save = Path.of( required( values, "saveDirectory" ) ).normalize();
            Path snapshots = Path.of( required( values, "snapshotDirectory" ) ).normalize();
            Path log = Path.of( required( values, "log" ) ).normalize();
            if ( !executable.equals( executable() ) || !save.startsWith( installation )
                    || !snapshots.startsWith( token.getParent() ) || snapshots.equals( token.getParent() )
                    || !log.startsWith( installation ) ) {
                throw new IOException( "Recovery token leaves its installation" );
            }
            ProcessIdentity identity = new ProcessIdentity( Long.parseLong( required( values, "pid" ) ),
                    Instant.parse( required( values, "started" ) ), executable );
            return Optional.of( new ManagedLaunch( required( values, "launch" ), identity,
                    new WorldContext( required( values, "world" ), save, snapshots ), log ) );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not read managed launch", exception );
        }
    }

    /** Deletes only the token, and only after the runtime has observed the managed process exit. */
    void clear() {
        try {
            if ( Files.exists( token, NOFOLLOW_LINKS ) ) {
                FileWorldSnapshotStore.checkDirectory( installation, token.getParent() );
                Files.delete( token );
            }
        } catch ( IOException exception ) {
            log.warn( "Could not clear obsolete recovery token", exception );
        }
    }

    /**
     * Preserves an invalid token for diagnosis after a process scan finds no surviving server.
     *
     * @throws UncheckedIOException
     *         if the token cannot be safely moved; a new launch must remain blocked
     */
    void quarantine() {
        try {
            FileWorldSnapshotStore.checkDirectory( installation, token.getParent() );
            Path invalid = token.resolveSibling( "server.pid.invalid-" + Instant.now().toEpochMilli()
                    + "-" + UUID.randomUUID() );
            Files.move( token, invalid, ATOMIC_MOVE );
            log.debug( "Moved invalid recovery token to quarantine" );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not quarantine invalid recovery token", exception );
        }
    }

    private static String required( Properties properties, String name ) {
        return requireNonNull( properties.getProperty( name ), "Missing recovery property " + name );
    }

    private static void deleteTemporary( Path file ) {
        try {
            Files.deleteIfExists( file );
        } catch ( IOException exception ) {
            log.warn( "Could not remove temporary recovery token", exception );
        }
    }
}
