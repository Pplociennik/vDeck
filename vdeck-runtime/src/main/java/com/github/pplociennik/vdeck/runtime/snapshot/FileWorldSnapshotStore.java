package com.github.pplociennik.vdeck.runtime.snapshot;

import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.runtime.api.WorldSaveMissingException;
import com.github.pplociennik.vdeck.runtime.spi.FileIdentityReader;
import com.github.pplociennik.vdeck.runtime.spi.WorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/**
 * Streams complete local world sets into an isolated staging directory, forces each file, and
 * publishes the set with ATOMIC_MOVE. Size, mtime, file identity and membership must remain stable.
 * This BEST_EFFORT check cannot prove semantic consistency or durability of the directory rename
 * against sudden power loss. The last complete snapshot is replaced only after a successful copy;
 * failed publication restores it, or leaves its .old directory available for crash recovery.
 * Calls must be serialized by the lifecycle worker, which also owns cancellation and deadlines.
 */
@Log4j2
public final class FileWorldSnapshotStore implements WorldSnapshotStore {

    private static final int MAX_FILES = 8192;
    private static final long MAX_BYTES = 8L * 1024 * 1024 * 1024;

    private final Path installation;

    private final FileIdentityReader identities;

    /**
     * Creates the writer with an administrator-selected installation and a reliable identity reader.
     * The installation is canonicalized once; validation never rejects redirects above that boundary.
     *
     * @param installation
     *         existing absolute installation root
     * @param identities
     *         filesystem identity boundary
     * @throws NullPointerException
     *         if either argument is null
     * @throws IllegalArgumentException
     *         if the installation is relative
     * @throws UncheckedIOException
     *         if the installation cannot be canonicalized
     */
    public FileWorldSnapshotStore( @Nonnull Path installation, @Nonnull FileIdentityReader identities ) {
        requireNonNull( installation, "installation must not be null" );
        this.identities = requireNonNull( identities, "identities must not be null" );
        if ( !installation.isAbsolute() ) {
            throw new IllegalArgumentException( "Snapshot installation must be absolute" );
        }
        try {
            this.installation = installation.toRealPath();
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not resolve snapshot installation", exception );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void create( @Nonnull WorldContext world, @Nonnull String operationId, @Nonnull BooleanSupplier allowed ) {
        requireNonNull( world, "world must not be null" );
        requireNonNull( operationId, "operationId must not be null" );
        requireNonNull( allowed, "allowed must not be null" );
        if ( !operationId.matches( "[a-zA-Z0-9-]{1,100}" ) ) {
            throw new IllegalArgumentException( "Invalid snapshot operation identifier" );
        }
        Path staging = null;
        try {
            Path root = world.snapshotDirectory();
            prepareRoot( root );
            checkAllowed( allowed );
            recover( root, allowed );
            Path source = world.saveDirectory().resolve( "worlds_local" );
            checkSourceDirectory( source );
            int copiedFiles = 0;
            for ( int attempt = 0; attempt < 3; attempt++ ) {
                checkAllowed( allowed );
                try {
                    Map< Path, Stamp > before = inventory( source, world.name() );
                    ensureCapacity( root, before );
                    staging = Files.createDirectory( root.resolve( ".tmp-" + UUID.randomUUID() ) );
                    Properties metadata = metadata( world.name(), operationId );
                    for ( var entry : before.entrySet() ) {
                        checkAllowed( allowed );
                        String checksum = copy( root, source.resolve( entry.getKey() ), staging.resolve( entry.getKey() ),
                                allowed, entry.getValue().size() );
                        metadata.setProperty( "sha256." + entry.getKey(), checksum );
                        metadata.setProperty( "size." + entry.getKey(), Long.toString( entry.getValue().size() ) );
                    }
                    if ( !before.equals( inventory( source, world.name() ) ) ) {
                        cleanup( root, staging );
                        staging = null;
                        log.warn( "World changed during snapshot attempt {} for operation {}", attempt + 1, operationId );
                        continue;
                    }
                    writeMetadata( staging.resolve( "snapshot.properties" ), metadata );
                    copiedFiles = before.size();
                    break;
                } catch ( IOException exception ) {
                    if ( staging != null ) {
                        cleanup( root, staging );
                        staging = null;
                    }
                    if ( attempt == 2 ) {
                        throw exception;
                    }
                    log.warn( "Snapshot attempt {} failed for operation {}; retrying", attempt + 1, operationId,
                            exception );
                }
            }
            if ( staging == null ) {
                throw new IOException( "World did not stabilize for a complete snapshot" );
            }
            checkDirectory( root, root );
            checkAllowed( allowed );
            publish( root, staging, allowed );
            staging = null;
            log.info( "Published BEST_EFFORT world snapshot, operation {}, {} files", operationId, copiedFiles );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "World snapshot failed", exception );
        } finally {
            if ( staging != null ) {
                cleanup( world.snapshotDirectory(), staging );
            }
        }
    }

    /**
     * Rejects redirects in a directory chain from a canonical trusted root downwards.
     *
     * @param root
     *         canonical absolute trusted root
     * @param directory
     *         existing absolute directory
     * @throws IOException
     *         if a directory leaves the root, redirects, is absent, or cannot be inspected
     * @throws NullPointerException
     *         if either path is null
     */
    public static void checkDirectory( @Nonnull Path root, @Nonnull Path directory ) throws IOException {
        requireNonNull( root, "root must not be null" );
        requireNonNull( directory, "directory must not be null" );
        Path normalized = directory.normalize();
        if ( !root.isAbsolute() || !normalized.isAbsolute() || !normalized.startsWith( root ) ) {
            throw new IOException( "Directory leaves its trusted root" );
        }
        for ( Path part = normalized; part.startsWith( root ); part = part.getParent() ) {
            BasicFileAttributes attributes = Files.readAttributes( part, BasicFileAttributes.class, NOFOLLOW_LINKS );
            if ( !attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                    || !part.toRealPath().equals( part ) ) {
                throw new IOException( "Redirected or invalid directory" );
            }
            if ( part.equals( root ) ) {
                break;
            }
        }
    }

    private void prepareRoot( Path root ) throws IOException {
        Path panelDirectory = installation.resolve( ".vDeck" );
        if ( !root.startsWith( panelDirectory ) || root.equals( panelDirectory ) ) {
            throw new IOException( "Snapshot root leaves the panel's .vDeck directory" );
        }
        Path existing = root;
        while ( !Files.exists( existing, NOFOLLOW_LINKS ) ) {
            existing = existing.getParent();
        }
        checkDirectory( installation, existing );
        Files.createDirectories( root );
        checkDirectory( installation, root );
    }

    private Map< Path, Stamp > inventory( Path source, String name ) throws IOException {
        checkSourceDirectory( source );
        Map< Path, Stamp > result = new LinkedHashMap<>();
        Path worldDirectory = source.resolve( name );
        if ( Files.exists( worldDirectory, NOFOLLOW_LINKS ) ) {
            checkDirectory( installation, worldDirectory );
            try ( var entries = Files.walk( worldDirectory, 16 ) ) {
                var iterator = entries.iterator();
                int visited = 0;
                while ( iterator.hasNext() ) {
                    if ( ++visited > MAX_FILES ) {
                        throw new IOException( "World directory entry limit exceeded" );
                    }
                    Path entry = iterator.next();
                    if ( Files.isDirectory( entry, NOFOLLOW_LINKS ) ) {
                        checkDirectory( installation, entry );
                        if ( worldDirectory.relativize( entry ).getNameCount() >= 16 ) {
                            throw new IOException( "World directory depth limit exceeded" );
                        }
                    } else {
                        addFile( result, source, entry );
                    }
                }
            }
        } else {
            for ( String extension : List.of( ".db", ".fwl" ) ) {
                try {
                    addFile( result, source, source.resolve( name + extension ) );
                } catch ( NoSuchFileException exception ) {
                    throw new WorldSaveMissingException( exception );
                }
            }
            for ( String extension : List.of( ".db.old", ".fwl.old" ) ) {
                Path entry = source.resolve( name + extension );
                if ( !Files.exists( entry, NOFOLLOW_LINKS ) ) {
                    continue;
                }
                addFile( result, source, entry );
            }
        }
        if ( result.isEmpty() || result.values().stream().allMatch( stamp -> stamp.size() == 0 ) ) {
            throw new IOException( "World contains no saved data" );
        }
        return result;
    }

    private void checkSourceDirectory( Path source ) throws IOException {
        try {
            checkDirectory( installation, source );
        } catch ( NoSuchFileException exception ) {
            throw new WorldSaveMissingException( exception );
        }
    }

    private void addFile( Map< Path, Stamp > result, Path source, Path file ) throws IOException {
        checkDirectory( installation, file.getParent() );
        BasicFileAttributes attributes = Files.readAttributes( file, BasicFileAttributes.class, NOFOLLOW_LINKS );
        if ( !attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                || attributes.size() == 0 || !file.toRealPath().equals( file ) || result.size() >= MAX_FILES ) {
            throw new IOException( "Invalid world file or file count limit exceeded" );
        }
        String identity = identities.identify( file );
        if ( identity == null || identity.isBlank() ) {
            throw new IOException( "Reliable world file identity is unavailable" );
        }
        result.put( source.relativize( file ), new Stamp( attributes.size(),
                attributes.lastModifiedTime().toInstant(), identity ) );
    }

    private static void ensureCapacity( Path root, Map< Path, Stamp > files ) throws IOException {
        long bytes = 0;
        for ( Stamp stamp : files.values() ) {
            bytes = Math.addExact( bytes, stamp.size() );
        }
        if ( bytes > MAX_BYTES || Files.getFileStore( root ).getUsableSpace() < bytes + 1024 * 1024 ) {
            throw new IOException( "Snapshot storage capacity exceeded" );
        }
    }

    private static String copy( Path root, Path source, Path target, BooleanSupplier allowed,
                                long expectedSize ) throws IOException {
        Files.createDirectories( target.getParent() );
        checkDirectory( root, target.getParent() );
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate( 64 * 1024 );
        long copied = 0;
        try ( var input = FileChannel.open( source, READ, NOFOLLOW_LINKS );
              var output = FileChannel.open( target, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            while ( input.read( buffer ) != -1 ) {
                checkAllowed( allowed );
                buffer.flip();
                copied += buffer.remaining();
                if ( copied > expectedSize ) {
                    throw new IOException( "World file grew during copying" );
                }
                digest.update( buffer.asReadOnlyBuffer() );
                while ( buffer.hasRemaining() ) {
                    output.write( buffer );
                }
                buffer.clear();
            }
            output.force( true );
            if ( copied != expectedSize ) {
                throw new IOException( "World file shrank during copying" );
            }
        }
        return HexFormat.of().formatHex( digest.digest() );
    }

    private static Properties metadata( String world, String operationId ) {
        Properties properties = new Properties();
        properties.setProperty( "world", world );
        properties.setProperty( "operation", operationId );
        properties.setProperty( "created", Instant.now().toString() );
        properties.setProperty( "reason", "PRE_STOP" );
        properties.setProperty( "trust", "BEST_EFFORT" );
        return properties;
    }

    private static void writeMetadata( Path file, Properties properties ) throws IOException {
        try ( var stream = Files.newOutputStream( file, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            properties.store( stream, "vDeck world snapshot" );
        }
        try ( var channel = FileChannel.open( file, WRITE, NOFOLLOW_LINKS ) ) {
            channel.force( true );
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance( "SHA-256" );
        } catch ( NoSuchAlgorithmException exception ) {
            throw new IllegalStateException( "JDK does not support SHA-256", exception );
        }
    }

    private static void checkAllowed( BooleanSupplier allowed ) {
        if ( Thread.currentThread().isInterrupted() || !allowed.getAsBoolean() ) {
            throw new CancellationException( "Snapshot operation expired or was cancelled" );
        }
    }

    private static void publish( Path root, Path staging, BooleanSupplier allowed ) throws IOException {
        Path latest = root.resolve( "snapshot-latest" );
        Path previous = root.resolve( ".old-" + UUID.randomUUID() );
        boolean replaced = Files.exists( latest, NOFOLLOW_LINKS );
        if ( replaced ) {
            checkDirectory( root, latest );
            Files.move( latest, previous, ATOMIC_MOVE );
        }
        try {
            checkAllowed( allowed );
            Files.move( staging, latest, ATOMIC_MOVE );
        } catch ( IOException | RuntimeException exception ) {
            if ( replaced ) {
                try {
                    Files.move( previous, latest, ATOMIC_MOVE );
                } catch ( IOException rollbackFailure ) {
                    exception.addSuppressed( rollbackFailure );
                }
            }
            throw exception;
        }
        if ( replaced ) {
            cleanup( root, previous );
        }
    }

    private static void recover( Path root, BooleanSupplier allowed ) throws IOException {
        Path latest = root.resolve( "snapshot-latest" );
        if ( !Files.exists( latest, NOFOLLOW_LINKS ) ) {
            try ( var entries = Files.newDirectoryStream( root, ".old-*" ) ) {
                for ( Path previous : entries ) {
                    checkAllowed( allowed );
                    checkDirectory( root, previous );
                    Files.move( previous, latest, ATOMIC_MOVE );
                    log.warn( "Restored the previous snapshot after interrupted publication" );
                    break;
                }
            }
        }
        if ( Files.exists( latest, NOFOLLOW_LINKS ) ) {
            checkDirectory( root, latest );
        }
        try ( var entries = Files.newDirectoryStream( root ) ) {
            for ( Path entry : entries ) {
                checkAllowed( allowed );
                String name = entry.getFileName().toString();
                if ( name.startsWith( ".tmp-" ) || name.startsWith( ".old-" ) ) {
                    cleanup( root, entry );
                }
            }
        }
    }

    private static void cleanup( Path root, Path staging ) {
        try {
            checkDirectory( root, staging );
            Files.walkFileTree( staging, Set.of(), 18, new SimpleFileVisitor<>() {
                private int visited;

                /** {@inheritDoc} */
                @Override
                public FileVisitResult preVisitDirectory( Path directory, BasicFileAttributes attributes )
                        throws IOException {
                    checkEntryLimit();
                    checkDirectory( staging, directory );
                    return FileVisitResult.CONTINUE;
                }

                /** {@inheritDoc} */
                @Override
                public FileVisitResult visitFile( Path file, BasicFileAttributes attributes ) throws IOException {
                    checkEntryLimit();
                    if ( !file.normalize().startsWith( staging ) || !attributes.isRegularFile()
                            || attributes.isSymbolicLink() || attributes.isOther() ) {
                        throw new IOException( "Unsafe snapshot cleanup entry" );
                    }
                    checkDirectory( staging, file.getParent() );
                    Files.delete( file );
                    return FileVisitResult.CONTINUE;
                }

                /** {@inheritDoc} */
                @Override
                public FileVisitResult postVisitDirectory( Path directory, IOException failure ) throws IOException {
                    if ( failure != null ) {
                        throw failure;
                    }
                    checkDirectory( root, directory );
                    Files.delete( directory );
                    return FileVisitResult.CONTINUE;
                }

                private void checkEntryLimit() throws IOException {
                    if ( ++visited > MAX_FILES * 2 ) {
                        throw new IOException( "Snapshot cleanup entry limit exceeded" );
                    }
                }
            } );
        } catch ( IOException exception ) {
            log.warn( "Could not clean obsolete snapshot directory", exception );
        }
    }

    /** Bounded file metadata used to compare a complete set across copying. */
    private record Stamp( long size, Instant modified, String key ) {
    }
}
