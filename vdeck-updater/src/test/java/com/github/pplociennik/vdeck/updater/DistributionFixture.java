package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.PropertiesMerger;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Builds small but complete distribution trees for updater tests and captures installation contents.
 */
final class DistributionFixture {

    private DistributionFixture() {
    }

    /**
     * Writes every required distribution file below a root. JARs declare the version and the updater JAR
     * the supported recovery protocol; other files contain the marker so versions differ in content.
     *
     * @param root
     *         package or installation root
     * @param version
     *         version written into the manifests
     * @param panelSettings
     *         contents of {@code vDeck.properties}
     * @throws IOException
     *         if a file cannot be written
     */
    static void writeDistribution( Path root, String version, String panelSettings ) throws IOException {
        for ( String file : DistributionItem.REQUIRED_FILES ) {
            Path target = root.resolve( file );
            Files.createDirectories( target.getParent() );
            if ( file.endsWith( ".jar" ) ) {
                writeJar( target, version, file.equals( UpdateLayout.UPDATER_JAR ) );
            } else {
                Files.writeString( target, file + " of " + version, StandardCharsets.UTF_8 );
            }
        }
        Files.createDirectories( root.resolve( "jre/lib" ) );
        Files.writeString( root.resolve( "jre/lib/modules" ), "modules of " + version );
        Files.writeString( root.resolve( DistributionItem.PANEL_SETTINGS.getPath() ), panelSettings );
        Files.writeString( root.resolve( DistributionItem.START_SETTINGS.getPath() ),
                "panel.address=127.0.0.1\npanel.port=8085\nupdate.enabled=true\n" );
        Files.writeString( root.resolve( DistributionItem.SERVER_SETTINGS.getPath() ), "server.name=vDeck\n" );
    }

    /**
     * Captures the digest of every file owned by the distribution or the user, ignoring update work files,
     * logs and lock files.
     *
     * @param installation
     *         installation root
     * @return relative path to SHA-256, sorted by path
     * @throws IOException
     *         if a file cannot be read
     */
    static Map< String, String > snapshot( Path installation ) throws IOException {
        Map< String, String > files = new TreeMap<>();
        try ( Stream< Path > walk = Files.walk( installation ) ) {
            for ( Path file : walk.filter( Files::isRegularFile ).toList() ) {
                String relative = installation.relativize( file ).toString().replace( '\\', '/' );
                if ( !relative.startsWith( ".vDeck/update/" ) && !relative.startsWith( ".vDeck/logs/" )
                        && !relative.startsWith( ".vDeck/handoff/" ) && !relative.endsWith( ".lock" ) ) {
                    files.put( relative, DurableFiles.sha256( file ) );
                }
            }
        }
        return files;
    }

    /**
     * Creates an installation of version {@code 1.0.0} with user data and customized settings, stages
     * version {@code 1.1.0} and persists a prepared journal for it.
     *
     * @param installation
     *         empty canonical installation directory
     * @param checkpoint
     *         checkpoint of the created journal
     * @return prepared transaction
     * @throws IOException
     *         if the fixture cannot be written
     */
    static Transaction prepareTransaction( Path installation, Checkpoint checkpoint ) throws IOException {
        writeDistribution( installation, "1.0.0", "kept=custom\nobsolete=old\n" );
        Files.writeString( installation.resolve( "vdeck.db" ), "user database" );
        UpdateLayout layout = new UpdateLayout( installation );
        writeDistribution( layout.stagedDirectory(), "1.1.0", "kept=default\nadded=new\n" );
        Map< String, String > original = snapshot( installation );
        TransactionFiles files = new TransactionFiles( layout );
        var operations = new UpdatePlan( layout, files, new PropertiesMerger() ).prepare();
        UpdateJournal journal = UpdateJournal.create( layout.journal(), "1.0.0", "1.1.0", operations, checkpoint );
        return new Transaction( layout, files, journal, original );
    }

    /**
     * Prepared transaction of {@link #prepareTransaction}.
     *
     * @param layout
     *         update layout
     * @param files
     *         file actions
     * @param journal
     *         prepared journal
     * @param original
     *         snapshot of the installation before any change
     */
    record Transaction( UpdateLayout layout, TransactionFiles files, UpdateJournal journal,
                        Map< String, String > original ) {
    }

    private static void writeJar( Path target, String version, boolean updater ) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put( Attributes.Name.MANIFEST_VERSION, "1.0" );
        attributes.put( Attributes.Name.IMPLEMENTATION_VERSION, version );
        if ( updater ) {
            attributes.putValue( UpdateLayout.RECOVERY_PROTOCOL_ATTRIBUTE,
                    Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ) );
        }
        try ( OutputStream output = Files.newOutputStream( target ); JarOutputStream jar = new JarOutputStream( output,
                manifest ) ) {
            jar.setComment( target.getFileName() + " " + version );
        }
    }
}
