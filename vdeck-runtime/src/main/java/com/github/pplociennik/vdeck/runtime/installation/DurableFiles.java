package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/**
 * File primitives shared by the launcher, updater and panel handoff: bounded reads of untrusted files,
 * durable writes published by an atomic rename, and SHA-256 digests.
 *
 * <p>A durable write fully writes a temporary file in the target directory, forces it to the device,
 * closes it and only then renames it over the target with {@code ATOMIC_MOVE}. There is no
 * non-atomic fallback: a file system without atomic rename fails the operation instead.
 */
public final class DurableFiles {

    private static final int BUFFER_SIZE = 64 * 1024;

    private DurableFiles() {
    }

    /**
     * Reads a small properties file, rejecting links and anything larger than the limit.
     *
     * @param file
     *         absolute file to read
     * @param maxBytes
     *         largest accepted size in bytes
     * @return loaded properties
     * @throws NullPointerException
     *         if the file is null
     * @throws IOException
     *         if the file is missing, redirected, too large or unreadable
     */
    public static @Nonnull Properties readProperties( @Nonnull Path file, int maxBytes ) throws IOException {
        requireNonNull( file, "file must not be null" );
        if ( !Files.isRegularFile( file, NOFOLLOW_LINKS ) ) {
            throw new IOException( "Not a regular file: " + file.getFileName() );
        }
        byte[] contents;
        try ( InputStream input = Files.newInputStream( file, READ, NOFOLLOW_LINKS ) ) {
            contents = input.readNBytes( maxBytes + 1 );
        }
        if ( contents.length > maxBytes ) {
            throw new IOException( "File exceeds its size limit: " + file.getFileName() );
        }
        Properties properties = new Properties();
        properties.load( new InputStreamReader( new ByteArrayInputStream( contents ), StandardCharsets.UTF_8 ) );
        return properties;
    }

    /**
     * Durably replaces a file with the stored form of the given properties.
     *
     * @param target
     *         absolute file to create or replace
     * @param properties
     *         values to store
     * @param comment
     *         header comment
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the file cannot be written, forced or renamed atomically
     */
    public static void writeProperties( @Nonnull Path target, @Nonnull Properties properties, @Nonnull String comment )
            throws IOException {
        requireNonNull( target, "target must not be null" );
        requireNonNull( properties, "properties must not be null" );
        requireNonNull( comment, "comment must not be null" );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try ( OutputStreamWriter writer = new OutputStreamWriter( output, StandardCharsets.UTF_8 ) ) {
            properties.store( writer, comment );
        }
        write( target, output.toByteArray() );
    }

    /**
     * Durably creates or replaces a file with the given bytes.
     *
     * @param target
     *         absolute file to create or replace
     * @param contents
     *         complete new contents
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the file cannot be written, forced or renamed atomically
     */
    public static void write( @Nonnull Path target, @Nonnull byte[] contents ) throws IOException {
        requireNonNull( target, "target must not be null" );
        requireNonNull( contents, "contents must not be null" );
        Path temporary = Files.createTempFile( target.getParent(), ".tmp-", ".part" );
        try {
            try ( FileChannel channel = FileChannel.open( temporary, WRITE, NOFOLLOW_LINKS ) ) {
                ByteBuffer remaining = ByteBuffer.wrap( contents );
                while ( remaining.hasRemaining() ) {
                    channel.write( remaining );
                }
                channel.force( true );
            }
            Files.move( temporary, target, REPLACE_EXISTING, ATOMIC_MOVE );
        } finally {
            Files.deleteIfExists( temporary );
        }
    }

    /**
     * Copies a regular file to a new file and forces the copy to the device. The target must not exist.
     *
     * @param source
     *         regular file to copy
     * @param target
     *         new file to create
     * @return SHA-256 of the copied bytes, as lowercase hexadecimal text
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the source is not a regular file or copying fails
     */
    public static @Nonnull String copyDurably( @Nonnull Path source, @Nonnull Path target ) throws IOException {
        requireNonNull( source, "source must not be null" );
        requireNonNull( target, "target must not be null" );
        if ( !Files.isRegularFile( source, NOFOLLOW_LINKS ) ) {
            throw new IOException( "Not a regular file: " + source.getFileName() );
        }
        MessageDigest digest = newDigest();
        try ( InputStream input = Files.newInputStream( source, READ, NOFOLLOW_LINKS );
              FileChannel output = FileChannel.open( target, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            byte[] buffer = new byte[ BUFFER_SIZE ];
            for ( int read = input.read( buffer ); read >= 0; read = input.read( buffer ) ) {
                digest.update( buffer, 0, read );
                ByteBuffer chunk = ByteBuffer.wrap( buffer, 0, read );
                while ( chunk.hasRemaining() ) {
                    output.write( chunk );
                }
            }
            output.force( true );
        }
        return HexFormat.of().formatHex( digest.digest() );
    }

    /**
     * Computes the SHA-256 digest of a regular file without following links.
     *
     * @param file
     *         regular file to hash
     * @return lowercase hexadecimal digest
     * @throws NullPointerException
     *         if the file is null
     * @throws IOException
     *         if the file is not a regular file or cannot be read
     */
    public static @Nonnull String sha256( @Nonnull Path file ) throws IOException {
        requireNonNull( file, "file must not be null" );
        if ( !Files.isRegularFile( file, NOFOLLOW_LINKS ) ) {
            throw new IOException( "Not a regular file: " + file.getFileName() );
        }
        MessageDigest digest = newDigest();
        try ( InputStream input = Files.newInputStream( file, READ, NOFOLLOW_LINKS ) ) {
            byte[] buffer = new byte[ BUFFER_SIZE ];
            for ( int read = input.read( buffer ); read >= 0; read = input.read( buffer ) ) {
                digest.update( buffer, 0, read );
            }
        }
        return HexFormat.of().formatHex( digest.digest() );
    }

    /**
     * Deletes a directory tree without following links. The tree root must be a real directory below the
     * trusted root; a link inside the tree is deleted as a link, never followed.
     *
     * @param trustedRoot
     *         canonical root that must contain the tree
     * @param tree
     *         directory to delete; a missing directory is ignored
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the tree is redirected or an entry cannot be deleted
     */
    public static void deleteTree( @Nonnull Path trustedRoot, @Nonnull Path tree ) throws IOException {
        requireNonNull( trustedRoot, "trustedRoot must not be null" );
        requireNonNull( tree, "tree must not be null" );
        if ( !Files.exists( tree, NOFOLLOW_LINKS ) ) {
            return;
        }
        if ( tree.equals( trustedRoot ) ) {
            throw new IOException( "Refusing to delete the trusted root" );
        }
        FileWorldSnapshotStore.checkDirectory( trustedRoot, tree );
        List< Path > entries;
        try ( Stream< Path > walk = Files.walk( tree ) ) {
            entries = walk.sorted( Comparator.reverseOrder() ).toList();
        }
        for ( Path entry : entries ) {
            Files.delete( entry );
        }
    }

    /**
     * Forces every regular file of a directory tree to the device, so files written by a library survive
     * a power loss before they are published. Links are neither followed nor forced.
     *
     * @param tree
     *         directory whose files are forced
     * @throws NullPointerException
     *         if the tree is null
     * @throws IOException
     *         if the tree cannot be walked or a file cannot be forced
     */
    public static void forceTree( @Nonnull Path tree ) throws IOException {
        requireNonNull( tree, "tree must not be null" );

        List< Path > files;
        try ( Stream< Path > walk = Files.walk( tree ) ) {
            files = walk.filter( entry -> Files.isRegularFile( entry, NOFOLLOW_LINKS ) ).toList();
        }

        for ( Path file : files ) {
            try ( FileChannel channel = FileChannel.open( file, WRITE, NOFOLLOW_LINKS ) ) {
                channel.force( true );
            }
        }
    }

    /**
     * Creates a SHA-256 digest, which every Java runtime must provide.
     *
     * @return new digest instance
     */
    public static @Nonnull MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance( "SHA-256" );
        } catch ( NoSuchAlgorithmException exception ) {
            throw new IllegalStateException( "SHA-256 is unavailable", exception );
        }
    }
}
