package com.github.pplociennik.vdeck.feature.releasenotes.service.impl;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link ReleaseNotesReader} reads every valid change list file, newest version first, and
 * skips badly named, unreadable or malformed files instead of failing.
 */
@ExtendWith( MockitoExtension.class )
class ReleaseNotesReaderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String VALID_CONTENT = "## Nowości\n- **Zmiana**\n  Opis zmiany.\n";

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    private ReleaseNotesReader underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a reader over the mocked classpath.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseNotesReader( resourcePatternResolver );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that versions are ordered numerically, newest first, regardless of the listing order.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldReturnNewestFirst_whenSeveralFilesExist() throws IOException {
        // GIVEN
        givenResources( file( "0.9.0.md", VALID_CONTENT ), file( "0.10.0.md", VALID_CONTENT ),
                file( "0.2.1.md", VALID_CONTENT ) );

        // WHEN
        var result = underTest.readAll();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly(
                new ReleaseVersion( 0, 10, 0 ), new ReleaseVersion( 0, 9, 0 ), new ReleaseVersion( 0, 2, 1 ) );
        assertThat( result.getFirst().sections() ).hasSize( 1 );
    }

    /**
     * Verifies that a file whose name is no version is skipped while the valid files are still read.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldSkipFile_whenNameIsNoVersion() throws IOException {
        // GIVEN
        givenResources( file( "template.md", VALID_CONTENT ), file( "1.0.0.md", VALID_CONTENT ) );

        // WHEN
        var result = underTest.readAll();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly( new ReleaseVersion( 1, 0, 0 ) );
    }

    /**
     * Verifies that a malformed file is skipped instead of breaking the home page.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldSkipFile_whenContentIsMalformed() throws IOException {
        // GIVEN
        givenResources( file( "1.0.0.md", "## Inne\n" ), file( "1.1.0.md", VALID_CONTENT ) );

        // WHEN
        var result = underTest.readAll();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly( new ReleaseVersion( 1, 1, 0 ) );
    }

    /**
     * Verifies that a file whose content cannot be read is skipped.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldSkipFile_whenContentCannotBeRead() throws IOException {
        // GIVEN
        Resource unreadable = new ByteArrayResource( new byte[ 0 ] ) {
            @Override
            public String getFilename() {
                return "1.0.0.md";
            }

            @Override
            public String getContentAsString( Charset charset ) throws IOException {
                throw new IOException( "read failure" );
            }
        };
        givenResources( unreadable );

        // WHEN
        var result = underTest.readAll();

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that an empty location yields no change lists.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldReturnEmptyList_whenNoFileExists() throws IOException {
        // GIVEN
        givenResources();

        // WHEN
        var result = underTest.readAll();

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a classpath that cannot be listed is reported as an I/O failure.
     *
     * @throws IOException
     *         never, the resolver is a mock
     */
    @Test
    void shouldThrowException_whenLocationCannotBeListed() throws IOException {
        // GIVEN
        given( resourcePatternResolver.getResources( ReleaseNotesReader.LOCATION_PATTERN ) )
                .willThrow( new IOException( "listing failure" ) );

        // WHEN
        var exception = catchThrowable( () -> underTest.readAll() );

        // THEN
        assertThat( exception ).isInstanceOf( UncheckedIOException.class ).hasRootCauseMessage( "listing failure" );
    }

    /**
     * Verifies that a file name made of a release version and the extension yields that version.
     */
    @Test
    void shouldReturnVersion_whenFileNameIsVersion() {
        // GIVEN
        String fileName = "2.11.0.md";

        // WHEN
        var result = ReleaseNotesReader.versionOf( fileName );

        // THEN
        assertThat( result ).contains( new ReleaseVersion( 2, 11, 0 ) );
    }

    /**
     * Verifies that any other file name yields no version.
     *
     * @param fileName
     *         rejected file name
     */
    @ParameterizedTest( name = "[{index}] fileName={0}" )
    @NullAndEmptySource
    @ValueSource( strings = { "1.0.0", "1.0.0.txt", "1.0.0-SNAPSHOT.md", "v1.0.0.md", ".md", "notes.md" } )
    void shouldReturnEmpty_whenFileNameIsNoVersion( String fileName ) {
        // GIVEN
        // a file name that is not <version>.md

        // WHEN
        var result = ReleaseNotesReader.versionOf( fileName );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a reader cannot be created without a resolver.
     */
    @Test
    void shouldThrowException_whenResolverIsNull() {
        // GIVEN
        // no resolver

        // WHEN
        var exception = catchThrowable( () -> new ReleaseNotesReader( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "resourcePatternResolver" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenResources( Resource... resources ) throws IOException {
        given( resourcePatternResolver.getResources( ReleaseNotesReader.LOCATION_PATTERN ) ).willReturn( resources );
    }

    private static Resource file( String fileName, String content ) {
        return new ByteArrayResource( content.getBytes( UTF_8 ) ) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
