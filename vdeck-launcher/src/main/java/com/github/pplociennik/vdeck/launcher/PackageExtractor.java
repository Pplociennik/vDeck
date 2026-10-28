package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.JarManifests;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import net.lingala.zip4j.ZipFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.Objects.requireNonNull;

/**
 * Extracts a digest-verified distribution package into {@code .vDeck/update/staged} (AD-024).
 *
 * <p>zip4j extracts the package into a fresh {@code .tmp-staged-<uuid>} directory and refuses entries
 * leaving it. The directory is renamed to {@code staged} only after its files are forced to the device,
 * every required file exists, the launcher and updater JARs declare the released version and the
 * updater declares the supported {@link UpdateLayout#RECOVERY_PROTOCOL}. The updater installs only the
 * {@link DistributionItem} entries, so other files in the package are never installed.
 */
@Log4j2
final class PackageExtractor {

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
     *         shared preparation deadline, checked before publication
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
            try ( ZipFile archive = new ZipFile( zip.toFile() ) ) {
                archive.extractAll( temporary.toString() );
            }
            DurableFiles.forceTree( temporary );

            verify( temporary, version );
            deadline.check();

            Files.move( temporary, layout.stagedDirectory(), ATOMIC_MOVE );
            published = true;
            log.info( "Extracted vDeck {} into the staged directory", version );
        } finally {
            if ( !published ) {
                DurableFiles.deleteTree( layout.installation(), temporary );
            }
        }
    }

    private static void verify( Path root, ReleaseVersion version ) throws IOException {
        List< String > missing = DistributionItem.missingFiles( root );
        if ( !missing.isEmpty() ) {
            throw new IOException( "The package is incomplete: " + missing );
        }

        Optional< String > expected = Optional.of( version.toString() );
        Optional< String > updater = manifestAttribute( root, UpdateLayout.UPDATER_JAR,
                JarManifests.IMPLEMENTATION_VERSION );
        Optional< String > launcher = manifestAttribute( root, UpdateLayout.LAUNCHER_JAR,
                JarManifests.IMPLEMENTATION_VERSION );
        if ( !expected.equals( updater ) || !expected.equals( launcher ) ) {
            throw new IOException( "The package contains another vDeck version" );
        }

        Optional< String > protocol = Optional.of( Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ) );
        if ( !protocol.equals( manifestAttribute( root, UpdateLayout.UPDATER_JAR,
                UpdateLayout.RECOVERY_PROTOCOL_ATTRIBUTE ) ) ) {
            throw new IOException( "The package uses an unsupported recovery protocol; install it manually" );
        }
    }

    private static Optional< String > manifestAttribute( Path root, String jar, String attribute ) throws IOException {
        return JarManifests.attribute( root.resolve( jar ), attribute );
    }
}
