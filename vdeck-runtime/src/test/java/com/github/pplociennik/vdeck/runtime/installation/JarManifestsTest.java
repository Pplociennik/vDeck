package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies manifest reads used to validate packages.
 */
class JarManifestsTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path jar;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a JAR declaring a version.
     */
    @BeforeEach
    void setUp() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put( Attributes.Name.MANIFEST_VERSION, "1.0" );
        manifest.getMainAttributes().put( Attributes.Name.IMPLEMENTATION_VERSION, "1.2.3" );
        jar = directory.resolve( "versioned.jar" );
        try ( OutputStream output = Files.newOutputStream( jar ); JarOutputStream ignored = new JarOutputStream( output,
                manifest ) ) {
            // The manifest is the only entry.
        }
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a present attribute is read and the JAR can be deleted afterwards.
     */
    @Test
    void shouldReadAttribute_whenManifestDeclaresIt() throws Exception {
        // GIVEN
        // The fixture JAR declares a version.

        // WHEN
        var version = JarManifests.attribute( jar, JarManifests.IMPLEMENTATION_VERSION );

        // THEN
        assertThat( version ).contains( "1.2.3" );
        Files.delete( jar );
    }

    /**
     * Verifies that a missing attribute is an empty result.
     */
    @Test
    void shouldReturnEmpty_whenAttributeIsMissing() throws Exception {
        // GIVEN
        // The fixture JAR has no protocol attribute.

        // WHEN
        var protocol = JarManifests.attribute( jar, UpdateLayout.RECOVERY_PROTOCOL_ATTRIBUTE );

        // THEN
        assertThat( protocol ).isEmpty();
    }

    /**
     * Verifies that a file that is no JAR is reported.
     */
    @Test
    void shouldThrowException_whenFileIsNoJar() throws Exception {
        // GIVEN
        Path text = Files.writeString( directory.resolve( "text.jar" ), "no zip" );

        // WHEN
        var failure = catchThrowable( () -> JarManifests.attribute( text, JarManifests.IMPLEMENTATION_VERSION ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> JarManifests.attribute( jar, null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
