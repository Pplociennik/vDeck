package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link PathServiceImpl} resolves configured paths against the installation directory and
 * rejects every path that would leave it.
 *
 * <p>Escapes through a symbolic link or a Windows junction are not covered here: creating one requires
 * developer mode or administrator rights, which would make the test non-deterministic. That branch is
 * verified manually.
 */
@ExtendWith( MockitoExtension.class )
class PathServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path baseDirectory;

    @Mock
    private ApplicationHome applicationHome;

    private Path realBaseDirectory;

    private PathServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service resolving paths against an isolated temporary installation directory.
     */
    @BeforeEach
    void setUp() throws IOException {
        realBaseDirectory = baseDirectory.toRealPath();
        given( applicationHome.getDir() ).willReturn( baseDirectory.toFile() );
        underTest = new PathServiceImpl( applicationHome, null );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the application home directory is used when no override is configured.
     */
    @Test
    void shouldUseApplicationHome_whenOverrideIsNotConfigured() {
        // GIVEN
        // the service created in setUp without an override

        // WHEN
        Path result = underTest.getBaseDirectory();

        // THEN
        assertThat( result ).isEqualTo( realBaseDirectory );
    }

    /**
     * Verifies that a missing or blank override falls back to the application home directory.
     */
    @ParameterizedTest( name = "[{index}] override=\"{0}\"" )
    @NullSource
    @ValueSource( strings = { "", "   " } )
    void shouldTreatBlankOverrideAsAbsent( String configuredDirectory ) {
        // GIVEN
        // an application home pointing at the temporary directory

        // WHEN
        underTest = new PathServiceImpl( applicationHome, configuredDirectory );

        // THEN
        assertThat( underTest.getBaseDirectory() ).isEqualTo( realBaseDirectory );
    }

    /**
     * Verifies that a configured override wins over the application home directory.
     */
    @Test
    void shouldUseConfiguredDirectory_whenOverrideIsSet() throws IOException {
        // GIVEN
        Path installation = Files.createDirectory( baseDirectory.resolve( "installation" ) );

        // WHEN
        underTest = new PathServiceImpl( applicationHome, installation.toString() );

        // THEN
        assertThat( underTest.getBaseDirectory() ).isEqualTo( installation.toRealPath() );
    }

    /**
     * Verifies that a relative path is resolved against the installation directory.
     */
    @Test
    void shouldResolveRelativePathAgainstBaseDirectory() {
        // GIVEN
        String relativePath = "vdeck-logs";

        // WHEN
        Path result = underTest.getAbsolutePath( relativePath );

        // THEN
        assertThat( result ).isEqualTo( realBaseDirectory.resolve( "vdeck-logs" ) );
    }

    /**
     * Verifies that an existing target is returned in its real form, with links already followed.
     */
    @Test
    void shouldReturnRealPath_whenTargetAlreadyExists() throws IOException {
        // GIVEN
        Path existingDirectory = Files.createDirectory( baseDirectory.resolve( "vdeck-logs" ) );

        // WHEN
        Path result = underTest.getAbsolutePath( "vdeck-logs" );

        // THEN
        assertThat( result ).isEqualTo( existingDirectory.toRealPath() );
    }

    /**
     * Verifies that a target which does not exist yet is resolved without being created.
     */
    @Test
    void shouldReturnResolvedPath_whenTargetDoesNotExist() {
        // GIVEN
        String relativePath = "not-created-yet";

        // WHEN
        Path result = underTest.getAbsolutePath( relativePath );

        // THEN
        assertThat( result ).isEqualTo( realBaseDirectory.resolve( "not-created-yet" ) );
        assertThat( result ).doesNotExist();
    }

    /**
     * Verifies that redundant path segments are normalized away.
     */
    @ParameterizedTest( name = "[{index}] path=\"{0}\"" )
    @ValueSource( strings = { "./vdeck-logs", "a/../vdeck-logs", "vdeck-logs/." } )
    void shouldNormalizePath_whenValueContainsRedundantSegments( String relativePath ) {
        // GIVEN
        // a value pointing at the same directory through redundant segments

        // WHEN
        Path result = underTest.getAbsolutePath( relativePath );

        // THEN
        assertThat( result ).isEqualTo( realBaseDirectory.resolve( "vdeck-logs" ) );
    }

    /**
     * Verifies that a path escaping the installation directory is rejected.
     */
    @ParameterizedTest( name = "[{index}] path=\"{0}\"" )
    @ValueSource( strings = { "..", "../outside", "vdeck-logs/../../outside" } )
    void shouldThrowException_whenPathEscapesBaseDirectory( String relativePath ) {
        // GIVEN
        // a value using parent segments to leave the installation directory

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getAbsolutePath( relativePath ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( PathResolutionException.class )
                .hasMessageContaining( "resolves outside the installation directory" );
    }

    /**
     * Verifies that an absolute path is rejected, because configured paths must be relative.
     */
    @Test
    void shouldThrowException_whenPathIsAbsolute() {
        // GIVEN
        String absolutePath = realBaseDirectory.resolve( "vdeck-logs" ).toString();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getAbsolutePath( absolutePath ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( PathResolutionException.class )
                .hasMessageContaining( "must be relative" );
    }

    /**
     * Verifies that a syntactically invalid path is rejected.
     */
    @Test
    void shouldThrowException_whenPathIsSyntacticallyInvalid() {
        // A NUL character is rejected by the path parser on every supported platform.
        // GIVEN
        String invalidPath = "vdeck" + ( char ) 0 + "logs";

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getAbsolutePath( invalidPath ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( PathResolutionException.class )
                .hasMessageContaining( "is not a valid path" );
    }

    /**
     * Verifies that a blank path is rejected before any resolution happens.
     */
    @ParameterizedTest( name = "[{index}] path=\"{0}\"" )
    @ValueSource( strings = { "", "   ", "\t" } )
    void shouldThrowException_whenPathIsBlank( String relativePath ) {
        // GIVEN
        // a value carrying no path at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getAbsolutePath( relativePath ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "relativePath must not be blank" );
    }

    /**
     * Verifies that a configured installation directory which does not exist aborts construction.
     */
    @Test
    void shouldThrowException_whenConfiguredDirectoryDoesNotExist() {
        // GIVEN
        String missingDirectory = baseDirectory.resolve( "missing" ).toString();

        // WHEN
        Throwable thrown = catchThrowable( () -> new PathServiceImpl( applicationHome, missingDirectory ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to resolve the real path" );
    }

    /**
     * Verifies that a configured installation directory pointing at a file aborts construction.
     */
    @Test
    void shouldThrowException_whenConfiguredDirectoryIsAFile() throws IOException {
        // GIVEN
        Path file = Files.createFile( baseDirectory.resolve( "not-a-directory" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> new PathServiceImpl( applicationHome, file.toString() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( PathResolutionException.class )
                .hasMessageContaining( "is not a directory" );
    }

    /**
     * Verifies that a null application home is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenApplicationHomeIsNull() {
        // GIVEN
        // no application home at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new PathServiceImpl( null, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "applicationHome must not be null" );
    }

    /**
     * Verifies that a null path is rejected before any resolution happens.
     */
    @Test
    void shouldThrowException_whenPathIsNull() {
        // GIVEN
        // no path at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getAbsolutePath( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "relativePath must not be null" );
    }
}
