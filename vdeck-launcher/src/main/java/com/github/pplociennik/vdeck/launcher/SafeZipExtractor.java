package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.JarManifests;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/**
 * Extracts a verified distribution package into {@code .vDeck/update/staged} without trusting its
 * contents (AD-024).
 *
 * <p>Entries are extracted into a fresh {@code .tmp-staged-<uuid>} directory, which is renamed to
 * {@code staged} only after the whole package passed every check:
 * <ul>
 *     <li>names are relative, use {@code /}, contain no {@code ..}, {@code .}, drive letters, alternate data
 *     streams, characters reserved by Windows, reserved device names or trailing dots and spaces;</li>
 *     <li>names differing only in letter case are rejected, because Windows would merge them;</li>
 *     <li>only entries of the closed {@link DistributionItem} list are accepted;</li>
 *     <li>entry count, path depth, total extracted size and per-entry compression ratio are limited, and
 *     sizes are counted from the actual bytes, never from the archive's headers;</li>
 *     <li>every required file exists, the launcher and updater JARs declare the released version and the
 *     updater declares the supported {@link UpdateLayout#RECOVERY_PROTOCOL}.</li>
 * </ul>
 * Every JAR opened for a manifest check is closed before publication.
 */
@Log4j2
final class SafeZipExtractor {

    /**
     * Largest number of entries.
     */
    static final int MAX_ENTRIES = 10_000;

    /**
     * Largest number of path segments.
     */
    static final int MAX_DEPTH = 16;

    /**
     * Largest total extracted size.
     */
    static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;

    /**
     * Largest ratio between extracted and compressed size of an entry larger than one MiB.
     */
    static final long MAX_COMPRESSION_RATIO = 100;

    private static final long RATIO_THRESHOLD_BYTES = 1024 * 1024;

    private static final Pattern RESERVED_NAME = Pattern.compile( "(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]|CONIN\\$|CONOUT\\$)" );

    private static final String FORBIDDEN_CHARACTERS = "<>:\"|?*\\";

    private static final Set< String > LIB_ENTRIES = Set.of( "lib", UpdateLayout.PANEL_JAR, "lib/vdeck-runner.jar",
            UpdateLayout.LAUNCHER_JAR, UpdateLayout.UPDATER_JAR );

    private static final Set< String > PARENT_DIRECTORIES = Set.of( ".vDeck", ".vDeck/config" );

    /**
     * Extracts, validates and publishes the package as the staged directory.
     *
     * @param zip
     *         downloaded and digest-verified package
     * @param layout
     *         update layout of the installation; {@code staged} must not exist
     * @param version
     *         released version the package must contain
     * @param deadline
     *         shared preparation deadline
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the package is unsafe, incomplete, of another version or the deadline expires; nothing
     *         is published then
     */
    void extract( @Nonnull Path zip, @Nonnull UpdateLayout layout, @Nonnull ReleaseVersion version,
                  @Nonnull Deadline deadline ) throws IOException {
        requireNonNull( zip, "zip must not be null" );
        requireNonNull( layout, "layout must not be null" );
        requireNonNull( version, "version must not be null" );
        requireNonNull( deadline, "deadline must not be null" );
        Path temporary = layout.updateDirectory().resolve( ".tmp-staged-" + UUID.randomUUID() );
        boolean published = false;
        try {
            Files.createDirectory( temporary );
            FileWorldSnapshotStore.checkDirectory( layout.installation(), temporary );
            int entries = extractEntries( zip, temporary, deadline );
            verify( temporary, version );
            deadline.check();
            Files.move( temporary, layout.stagedDirectory(), ATOMIC_MOVE );
            published = true;
            log.info( "Extracted {} entries of vDeck {} into the staged directory", entries, version );
        } finally {
            if ( !published ) {
                DurableFiles.deleteTree( layout.installation(), temporary );
            }
        }
    }

    private int extractEntries( Path zip, Path root, Deadline deadline ) throws IOException {
        Set< String > seen = new HashSet<>();
        long total = 0;
        int count = 0;
        try ( ZipFile archive = new ZipFile( zip.toFile() ) ) {
            Enumeration< ? extends ZipEntry > entries = archive.entries();
            while ( entries.hasMoreElements() ) {
                ZipEntry entry = entries.nextElement();
                if ( ++count > MAX_ENTRIES ) {
                    throw new IOException( "The package has too many entries" );
                }
                deadline.check();
                String name = validName( entry.getName() );
                if ( !seen.add( name.toLowerCase( Locale.ROOT ) ) ) {
                    throw new IOException( "The package has duplicate or case-colliding entries" );
                }
                Path target = root.resolve( name ).normalize();
                if ( !target.startsWith( root ) || target.equals( root ) ) {
                    throw new IOException( "A package entry leaves its directory" );
                }
                if ( entry.isDirectory() ) {
                    Files.createDirectories( target );
                    continue;
                }
                Files.createDirectories( target.getParent() );
                try ( InputStream input = archive.getInputStream( entry ) ) {
                    total += write( input, target, entry, MAX_TOTAL_BYTES - total, deadline );
                }
            }
        }
        return count;
    }

    private static long write( InputStream input, Path target, ZipEntry entry, long allowed, Deadline deadline )
            throws IOException {
        long written = 0;
        try ( FileChannel output = FileChannel.open( target, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            byte[] buffer = new byte[ 64 * 1024 ];
            for ( int read = input.read( buffer ); read >= 0; read = input.read( buffer ) ) {
                written += read;
                if ( written > allowed ) {
                    throw new IOException( "The package exceeds its extracted size limit" );
                }
                long compressed = entry.getCompressedSize();
                if ( written > RATIO_THRESHOLD_BYTES && compressed > 0 && written / compressed > MAX_COMPRESSION_RATIO ) {
                    throw new IOException( "A package entry has a suspicious compression ratio" );
                }
                ByteBuffer chunk = ByteBuffer.wrap( buffer, 0, read );
                while ( chunk.hasRemaining() ) {
                    output.write( chunk );
                }
                deadline.check();
            }
            output.force( true );
        }
        return written;
    }

    /**
     * Validates an entry name and returns it without a trailing slash.
     */
    static @Nonnull String validName( @Nonnull String raw ) throws IOException {
        String name = raw.endsWith( "/" ) ? raw.substring( 0, raw.length() - 1 ) : raw;
        if ( name.isEmpty() || name.length() > 240 || name.startsWith( "/" ) ) {
            throw new IOException( "Invalid package entry name" );
        }
        String[] segments = name.split( "/", -1 );
        if ( segments.length > MAX_DEPTH ) {
            throw new IOException( "A package entry is nested too deeply" );
        }
        for ( String segment : segments ) {
            if ( !isValidSegment( segment ) ) {
                throw new IOException( "Invalid package entry name" );
            }
        }
        if ( !isAllowed( name ) ) {
            throw new IOException( "The package contains an unexpected entry" );
        }
        return name;
    }

    private static boolean isValidSegment( String segment ) {
        if ( segment.isEmpty() || segment.equals( "." ) || segment.equals( ".." ) || segment.endsWith( "." )
                || segment.endsWith( " " ) ) {
            return false;
        }
        for ( int index = 0; index < segment.length(); index++ ) {
            char character = segment.charAt( index );
            if ( character < 0x20 || character == 0x7f || FORBIDDEN_CHARACTERS.indexOf( character ) >= 0 ) {
                return false;
            }
        }
        String base = segment.contains( "." ) ? segment.substring( 0, segment.indexOf( '.' ) ) : segment;
        return !RESERVED_NAME.matcher( base.strip().toUpperCase( Locale.ROOT ) ).matches();
    }

    private static boolean isAllowed( String name ) {
        if ( name.equals( "jre" ) || name.startsWith( "jre/" ) || LIB_ENTRIES.contains( name )
                || PARENT_DIRECTORIES.contains( name ) ) {
            return true;
        }
        Optional< DistributionItem > item = DistributionItem.ofPath( name );
        return item.isPresent() && item.get().getKind() != DistributionItem.Kind.DIRECTORY;
    }

    private static void verify( Path root, ReleaseVersion version ) throws IOException {
        List< String > missing = DistributionItem.missingFiles( root );
        if ( !missing.isEmpty() ) {
            throw new IOException( "The package is incomplete: " + missing );
        }
        Optional< String > expected = Optional.of( version.toString() );
        if ( !expected.equals( JarManifests.attribute( root.resolve( UpdateLayout.UPDATER_JAR ),
                JarManifests.IMPLEMENTATION_VERSION ) )
                || !expected.equals( JarManifests.attribute( root.resolve( UpdateLayout.LAUNCHER_JAR ),
                JarManifests.IMPLEMENTATION_VERSION ) ) ) {
            throw new IOException( "The package contains another vDeck version" );
        }
        if ( !Optional.of( Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ) ).equals( JarManifests.attribute(
                root.resolve( UpdateLayout.UPDATER_JAR ), UpdateLayout.RECOVERY_PROTOCOL_ATTRIBUTE ) ) ) {
            throw new IOException( "The package uses an unsupported recovery protocol; install it manually" );
        }
    }
}
