package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
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
 * Verifies that only a complete package of the expected version is ever published as staged.
 */
class PackageExtractorTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion VERSION = new ReleaseVersion( 1, 1, 0 );

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private Deadline deadline;

    private PackageExtractor underTest;

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
        underTest = new PackageExtractor();
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
     * Verifies that an entry leaving the extraction directory is refused and nothing is published.
     *
     * @param name
     *         malicious entry name
     */
    @ParameterizedTest( name = "[{index}] entry={0}" )
    @ValueSource( strings = { "../escape.txt", "jre/../../escape.txt" } )
    void shouldRejectPackage_whenEntryLeavesDirectory( String name ) throws Exception {
        // GIVEN
        Map< String, byte[] > entries = PackageFixture.entries( "1.1.0" );
        entries.put( name, new byte[] { 1 } );
        Path zip = PackageFixture.zip( directory.resolve( "package.zip" ), entries );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( layout.stagedDirectory() ).doesNotExist();
        assertThat( layout.updateDirectory().resolve( "escape.txt" ) ).doesNotExist();
        assertThat( layout.installation().resolve( "escape.txt" ) ).doesNotExist();
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).isEmpty();
        }
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
        assertThat( layout.stagedDirectory() ).doesNotExist();
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
        assertThat( layout.stagedDirectory() ).doesNotExist();
    }

    /**
     * Verifies that a file that is not a ZIP archive is refused.
     */
    @Test
    void shouldRejectPackage_whenFileIsNotArchive() throws Exception {
        // GIVEN
        Path zip = Files.writeString( directory.resolve( "package.zip" ), "not a zip" );

        // WHEN
        var failure = catchThrowable( () -> underTest.extract( zip, layout, VERSION, deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( layout.stagedDirectory() ).doesNotExist();
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
