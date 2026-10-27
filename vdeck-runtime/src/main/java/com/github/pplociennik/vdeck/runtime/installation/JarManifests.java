package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Reads main attributes of JAR manifests. Every JAR is closed before the method returns, so validation
 * never leaves a handle that would block a later rename on Windows.
 */
public final class JarManifests {

    /**
     * Standard manifest attribute holding the Maven project version.
     */
    public static final String IMPLEMENTATION_VERSION = Attributes.Name.IMPLEMENTATION_VERSION.toString();

    private JarManifests() {
    }

    /**
     * Reads one main manifest attribute.
     *
     * @param jar
     *         regular JAR file, not a link
     * @param attribute
     *         attribute name
     * @return attribute value, or an empty optional when the manifest or attribute is missing
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the file is not a regular file or not a readable JAR
     */
    public static @Nonnull Optional< String > attribute( @Nonnull Path jar, @Nonnull String attribute )
            throws IOException {
        requireNonNull( jar, "jar must not be null" );
        requireNonNull( attribute, "attribute must not be null" );
        if ( !Files.isRegularFile( jar, NOFOLLOW_LINKS ) ) {
            throw new IOException( "Not a regular JAR file: " + jar.getFileName() );
        }
        try ( JarFile file = new JarFile( jar.toFile(), false ) ) {
            Manifest manifest = file.getManifest();
            return manifest == null ? Optional.empty()
                    : Optional.ofNullable( manifest.getMainAttributes().getValue( attribute ) );
        }
    }
}
