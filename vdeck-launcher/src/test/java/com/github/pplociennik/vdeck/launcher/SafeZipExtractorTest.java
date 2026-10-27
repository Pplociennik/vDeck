package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that only a safe, complete package of the expected version is ever published as staged.
 */
class SafeZipExtractorTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion VERSION = new ReleaseVersion( 1, 1, 0 );

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private Deadline deadline;

    private SafeZipExtractor underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation with an update directory.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        Files.createDirectories( layout.updateDirectory() );
        deadline = Deadline.after( Duration.ofMinutes( 1 ) );
        underTest = new SafeZipExtractor();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a complete package is published as the staged directory and no temporary directory
     * remains.
     */
    @Test
    void shouldPublishStaged_whenPackageIsValid() throws Exception {
        // GIVEN
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), PackageFixture.entries( "1.1.0" ) );

        // WHEN
        underTest.extract( zip, layout, VERSION, deadline );

        // THEN
        assertThat( layout.stagedDirectory().resolve( "jre/lib/modules" ) ).hasContent( "modules 1.1.0" );
        assertThat( layout.stagedDirectory().resolve( ".vDeck/config/vDeck-start.properties" ) ).exists();
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).containsExactly( layout.stagedDirectory() );
        }
    }

    /**
     * Verifies that dangerous or unexpected entry names are refused and nothing is published.
     *
     * @param name
     *         malicious entry name
     */
    @ParameterizedTest( name = "[{index}] entry={0}" )
    @ValueSource( strings = { "../escape.txt", "jre/../../escape.txt", "/absolute.txt", "C:/drive.txt",
            "jre/bin/java.exe:stream", "jre/CON.txt", "jre/nul", "jre/trailing.", "jre/back\\slash",
            "vdeck.db", ".vDeck/server.pid", "lib/extra.jar", "jre/./bin" } )
    void shouldRejectPackage_whenEntryNameIsUnsafe( String name ) throws Exception {
        // GIVEN
        Map< String, byte[] > entries = PackageFixture.entries( "1.1.0" );
        entries.put( name, new byte[] { 1 } );
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), entries );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( layout.stagedDirectory() ).doesNotExist();
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).isEmpty();
        }
    }

    /**
     * Verifies that names differing only in letter case, which Windows would merge, are refused.
     */
    @Test
    void shouldRejectPackage_whenNamesCollideIgnoringCase() throws Exception {
        // GIVEN
        Map< String, byte[] > entries = PackageFixture.entries( "1.1.0" );
        entries.put( "jre/lib/MODULES", new byte[] { 1 } );
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), entries );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "colliding" );
    }

    /**
     * Verifies that an entry with an extreme compression ratio is treated as a ZIP bomb.
     */
    @Test
    void shouldRejectPackage_whenEntryIsCompressionBomb() throws Exception {
        // GIVEN
        Map< String, byte[] > entries = PackageFixture.entries( "1.1.0" );
        entries.put( "jre/lib/bomb", new byte[ 8 * 1024 * 1024 ] );
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), entries );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "compression ratio" );
        assertThat( layout.stagedDirectory() ).doesNotExist();
    }

    /**
     * Verifies that a package without a required file is refused.
     */
    @Test
    void shouldRejectPackage_whenRequiredFileIsMissing() throws Exception {
        // GIVEN
        Map< String, byte[] > entries = PackageFixture.entries( "1.1.0" );
        entries.remove( "jre/bin/javaw.exe" );
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), entries );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "jre/bin/javaw.exe" );
    }

    /**
     * Verifies that a package of another version than the release is refused.
     */
    @Test
    void shouldRejectPackage_whenJarsDeclareAnotherVersion() throws Exception {
        // GIVEN
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), PackageFixture.entries( "1.0.9" ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "another vDeck version" );
    }

    /**
     * Verifies that an expired deadline stops the extraction before anything is published.
     */
    @Test
    void shouldPublishNothing_whenDeadlineHasExpired() throws Exception {
        // GIVEN
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), PackageFixture.entries( "1.1.0" ) );
        Deadline expired = Deadline.after( Duration.ZERO, () -> 0L );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, expired ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "deadline" );
        assertThat( layout.stagedDirectory() ).doesNotExist();
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( null, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
