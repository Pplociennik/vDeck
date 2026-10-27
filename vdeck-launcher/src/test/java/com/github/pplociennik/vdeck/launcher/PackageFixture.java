package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds distribution packages for launcher tests.
 */
final class PackageFixture {

    private PackageFixture() {
    }

    /**
     * Returns the entries of a complete package of the given version, in archive order, including the
     * directory entries Maven Assembly writes.
     *
     * @param version
     *         version declared by the JAR manifests
     * @return entry name to contents; directories end with {@code /} and have no contents
     * @throws IOException
     *         if a JAR cannot be built
     */
    static Map< String, byte[] > entries( String version ) throws IOException {
        Map< String, byte[] > entries = new LinkedHashMap<>();
        entries.put( ".vDeck/", null );
        entries.put( ".vDeck/config/", null );
        entries.put( "lib/", null );
        entries.put( "jre/", null );
        entries.put( "jre/bin/", null );
        entries.put( "jre/lib/", null );
        for ( String file : DistributionItem.REQUIRED_FILES ) {
            entries.put( file, file.endsWith( ".jar" ) ? jar( version, file.equals( UpdateLayout.UPDATER_JAR ) )
                    : ( file + " " + version ).getBytes( StandardCharsets.UTF_8 ) );
        }
        entries.put( "jre/lib/modules", ( "modules " + version ).getBytes( StandardCharsets.UTF_8 ) );
        return entries;
    }

    /**
     * Writes a ZIP archive.
     *
     * @param zip
     *         archive to create
     * @param entries
     *         entry name to contents; {@code null} contents create a directory entry
     * @return the archive
     * @throws IOException
     *         if the archive cannot be written
     */
    static Path zip( Path zip, Map< String, byte[] > entries ) throws IOException {
        try ( OutputStream output = Files.newOutputStream( zip ); ZipOutputStream archive = new ZipOutputStream( output ) ) {
            for ( Map.Entry< String, byte[] > entry : entries.entrySet() ) {
                archive.putNextEntry( new ZipEntry( entry.getKey() ) );
                if ( entry.getValue() != null ) {
                    archive.write( entry.getValue() );
                }
                archive.closeEntry();
            }
        }
        return zip;
    }

    /**
     * Writes the entries of a package as a directory tree, like a published staged directory.
     *
     * @param root
     *         target directory
     * @param version
     *         version declared by the JAR manifests
     * @throws IOException
     *         if a file cannot be written
     */
    static void writeTree( Path root, String version ) throws IOException {
        for ( Map.Entry< String, byte[] > entry : entries( version ).entrySet() ) {
            Path target = root.resolve( entry.getKey() );
            if ( entry.getValue() == null ) {
                Files.createDirectories( target );
            } else {
                Files.createDirectories( target.getParent() );
                Files.write( target, entry.getValue() );
            }
        }
    }

    private static byte[] jar( String version, boolean updater ) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put( Attributes.Name.MANIFEST_VERSION, "1.0" );
        attributes.put( Attributes.Name.IMPLEMENTATION_VERSION, version );
        if ( updater ) {
            attributes.putValue( UpdateLayout.RECOVERY_PROTOCOL_ATTRIBUTE,
                    Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ) );
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try ( JarOutputStream ignored = new JarOutputStream( bytes, manifest ) ) {
            // The manifest is the only entry.
        }
        return bytes.toByteArray();
    }
}
