package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Verifies the streaming download with size and digest verification and its progress messages. Timing of
 * stalled and slow bodies is covered by {@link StalledResponseIntegrationTest}.
 */
@ExtendWith( MockitoExtension.class )
class ReleaseDownloaderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final byte[] CONTENT = "package".getBytes( StandardCharsets.UTF_8 );

    @TempDir
    private Path directory;

    @Mock
    private HttpClient client;

    @Mock
    private HttpResponse< InputStream > response;

    @Mock
    private UserConsole console;

    private ReleaseDownloader underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the downloader with a one-second stall timeout and no progress messages during the tests.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseDownloader( client, console, Duration.ofSeconds( 1 ), Duration.ofDays( 1 ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a package of exactly the declared size and digest is published under its name.
     */
    @Test
    void shouldPublishPackage_whenSizeAndDigestMatch() throws Exception {
        // GIVEN
        respond( 200, CONTENT );

        // WHEN
        Path zip = underTest.download( asset( CONTENT.length, digest( CONTENT ) ), directory, deadline() );

        // THEN
        assertThat( zip ).isEqualTo( directory.resolve( "vDeck-dist-1.2.0.zip" ) ).hasBinaryContent( CONTENT );
        assertThat( directory.resolve( "vDeck-dist-1.2.0.zip.part" ) ).doesNotExist();
    }

    /**
     * Verifies that the received amount, the total size and the percentage are shown in the console.
     */
    @Test
    void shouldShowProgress_whenIntervalHasPassed() throws Exception {
        // GIVEN
        underTest = new ReleaseDownloader( client, console, Duration.ofSeconds( 1 ), Duration.ZERO );
        respond( 200, CONTENT );

        // WHEN
        underTest.download( asset( CONTENT.length, digest( CONTENT ) ), directory, deadline() );

        // THEN
        then( console ).should().show( eq( LauncherMessage.DOWNLOAD_PROGRESS ), any(), any(), eq( 100L ), any() );
    }

    /**
     * Verifies that another digest publishes nothing and removes the partial file.
     */
    @Test
    void shouldPublishNothing_whenDigestDiffers() throws Exception {
        // GIVEN
        respond( 200, CONTENT );

        // WHEN
        var failure = catchThrowable( () -> underTest.download( asset( CONTENT.length, "00".repeat( 32 ) ), directory,
                deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "digest" );
        assertEmptyDirectory();
    }

    /**
     * Verifies that a body longer than declared is cut off and refused.
     */
    @Test
    void shouldPublishNothing_whenBodyIsLongerThanDeclared() throws Exception {
        // GIVEN
        respond( 200, CONTENT );

        // WHEN
        var failure = catchThrowable( () -> underTest.download( asset( 3, digest( CONTENT ) ), directory, deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "larger" );
        assertEmptyDirectory();
    }

    /**
     * Verifies that a truncated body is refused.
     */
    @Test
    void shouldPublishNothing_whenBodyIsTruncated() throws Exception {
        // GIVEN
        respond( 200, CONTENT );

        // WHEN
        var failure = catchThrowable( () -> underTest.download( asset( 100, digest( CONTENT ) ), directory,
                deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "shorter" );
        assertEmptyDirectory();
    }

    /**
     * Verifies that a failed HTTP status is refused.
     */
    @Test
    void shouldFail_whenStatusIsNotOk() throws Exception {
        // GIVEN
        respond( 403, new byte[ 0 ] );

        // WHEN
        var failure = catchThrowable( () -> underTest.download( asset( 1, digest( CONTENT ) ), directory, deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "403" );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.download( null, directory, deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static Deadline deadline() {
        return Deadline.after( Duration.ofMinutes( 1 ) );
    }

    private static ReleaseAsset asset( long size, String sha256 ) {
        return new ReleaseAsset( new ReleaseVersion( 1, 2, 0 ), "vDeck-dist-1.2.0.zip", size, sha256,
                URI.create( "https://github.com/Pplociennik/vDeck/releases/download/v1.2.0/vDeck-dist-1.2.0.zip" ) );
    }

    private static String digest( byte[] content ) {
        return HexFormat.of().formatHex( DurableFiles.newDigest().digest( content ) );
    }

    private void respond( int status, byte[] body ) throws Exception {
        given( response.statusCode() ).willReturn( status );
        given( response.body() ).willReturn( new ByteArrayInputStream( body ) );
        given( client.send( any(), any() ) ).willAnswer( invocation -> response );
    }

    private void assertEmptyDirectory() throws IOException {
        try ( var files = Files.list( directory ) ) {
            assertThat( files ).isEmpty();
        }
    }
}
