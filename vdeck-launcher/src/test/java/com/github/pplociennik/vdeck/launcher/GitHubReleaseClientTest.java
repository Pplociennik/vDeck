package com.github.pplociennik.vdeck.launcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Verifies the validation of the latest GitHub release with a stubbed HTTP client.
 */
@ExtendWith( MockitoExtension.class )
class GitHubReleaseClientTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String DIGEST = "abababababababababababababababababababababababababababababababab";

    private static final String URL = "https://github.com/Pplociennik/vDeck/releases/download/v1.2.0/vDeck-dist-1.2.0.zip";

    @Mock
    private HttpClient client;

    @Mock
    private HttpResponse< InputStream > response;

    private GitHubReleaseClient underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the client for installed version 1.1.0.
     */
    @BeforeEach
    void setUp() {
        underTest = new GitHubReleaseClient( client, GitHubReleaseClient.LATEST_RELEASE, new ReleaseVersion( 1, 1, 0 ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a valid release yields its asset and that the request identifies vDeck to the API.
     */
    @Test
    void shouldReturnAsset_whenReleaseIsValid() throws Exception {
        // GIVEN
        respond( 200, release( "v1.2.0", "vDeck-dist-1.2.0.zip", 1234, "sha256:" + DIGEST, URL ) );

        // WHEN
        Optional< ReleaseAsset > asset = underTest.fetchLatest( deadline() );

        // THEN
        assertThat( asset ).contains( new ReleaseAsset( new ReleaseVersion( 1, 2, 0 ), "vDeck-dist-1.2.0.zip", 1234,
                DIGEST, URI.create( URL ) ) );
        ArgumentCaptor< HttpRequest > request = ArgumentCaptor.forClass( HttpRequest.class );
        then( client ).should().send( request.capture(), any() );
        assertThat( request.getValue().uri() ).isEqualTo( GitHubReleaseClient.LATEST_RELEASE );
        assertThat( request.getValue().headers().firstValue( "User-Agent" ) ).contains( "vDeck/1.1.0" );
    }

    /**
     * Verifies that a repository without a release means no update.
     */
    @Test
    void shouldReturnEmpty_whenRepositoryHasNoRelease() throws Exception {
        // GIVEN
        respond( 404, "{}" );

        // WHEN
        Optional< ReleaseAsset > asset = underTest.fetchLatest( deadline() );

        // THEN
        assertThat( asset ).isEmpty();
    }

    /**
     * Verifies that every invalid release description is refused.
     *
     * @param tag
     *         release tag
     * @param name
     *         asset name
     * @param size
     *         asset size
     * @param digest
     *         asset digest
     * @param url
     *         asset download URL
     */
    @ParameterizedTest( name = "[{index}] tag={0} name={1} size={2} digest={3} url={4}" )
    @CsvSource( {
            "1.2.0, vDeck-dist-1.2.0.zip, 10, sha256:" + DIGEST + ", " + URL,
            "v1.2.0-rc1, vDeck-dist-1.2.0.zip, 10, sha256:" + DIGEST + ", " + URL,
            "v1.2.0, vDeck-dist-1.2.1.zip, 10, sha256:" + DIGEST + ", " + URL,
            "v1.2.0, vDeck-dist-1.2.0.zip, 0, sha256:" + DIGEST + ", " + URL,
            "v1.2.0, vDeck-dist-1.2.0.zip, 536870913, sha256:" + DIGEST + ", " + URL,
            "v1.2.0, vDeck-dist-1.2.0.zip, 10, md5:abcd, " + URL,
            "v1.2.0, vDeck-dist-1.2.0.zip, 10, sha256:" + DIGEST
                    + ", http://github.com/Pplociennik/vDeck/releases/download/v1.2.0/vDeck-dist-1.2.0.zip",
            "v1.2.0, vDeck-dist-1.2.0.zip, 10, sha256:" + DIGEST
                    + ", https://evil.example/Pplociennik/vDeck/releases/download/v1.2.0/vDeck-dist-1.2.0.zip",
            "v1.2.0, vDeck-dist-1.2.0.zip, 10, sha256:" + DIGEST
                    + ", https://github.com/other/repo/releases/download/v1.2.0/vDeck-dist-1.2.0.zip" } )
    void shouldRejectRelease_whenDescriptionIsInvalid( String tag, String name, long size, String digest, String url )
            throws Exception {
        // GIVEN
        respond( 200, release( tag, name, size, digest, url ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.fetchLatest( deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that a draft or pre-release is refused.
     */
    @Test
    void shouldRejectRelease_whenItIsPrerelease() throws Exception {
        // GIVEN
        respond( 200, release( "v1.2.0", "vDeck-dist-1.2.0.zip", 10, "sha256:" + DIGEST, URL )
                .replace( "{", "{\"prerelease\":true," ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.fetchLatest( deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "pre-release" );
    }

    /**
     * Verifies that a response above one MiB is refused.
     */
    @Test
    void shouldRejectResponse_whenItExceedsSizeLimit() throws Exception {
        // GIVEN
        respond( 200, "{\"padding\":\"" + "x".repeat( GitHubReleaseClient.MAX_RESPONSE_BYTES ) + "\"}" );

        // WHEN
        var failure = catchThrowable( () -> underTest.fetchLatest( deadline() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "size limit" );
    }

    /**
     * Verifies that malformed JSON and server errors are refused.
     */
    @Test
    void shouldRejectResponse_whenItIsMalformedOrFailed() throws Exception {
        // GIVEN
        respond( 200, "{\"tag_name\":" );

        // WHEN
        var malformed = catchThrowable( () -> underTest.fetchLatest( deadline() ) );

        // THEN
        assertThat( malformed ).isInstanceOf( IOException.class ).hasMessageContaining( "JSON" );
    }

    /**
     * Verifies that an expired deadline prevents any request.
     */
    @Test
    void shouldFail_whenDeadlineHasExpired() {
        // GIVEN
        Deadline expired = Deadline.after( Duration.ZERO, () -> 0L );

        // WHEN
        var failure = catchThrowable( () -> underTest.fetchLatest( expired ) );

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
        var missingDeadline = catchThrowable( () -> underTest.fetchLatest( null ) );
        var missingClient = catchThrowable( () -> new GitHubReleaseClient( null, GitHubReleaseClient.LATEST_RELEASE,
                new ReleaseVersion( 1, 0, 0 ) ) );

        // THEN
        assertThat( missingDeadline ).isInstanceOf( NullPointerException.class );
        assertThat( missingClient ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static Deadline deadline() {
        return Deadline.after( Duration.ofMinutes( 1 ) );
    }

    private void respond( int status, String body ) throws Exception {
        given( response.statusCode() ).willReturn( status );
        given( response.body() ).willReturn( new ByteArrayInputStream( body.getBytes( StandardCharsets.UTF_8 ) ) );
        given( client.send( any(), any() ) ).willAnswer( invocation -> response );
    }

    private static String release( String tag, String name, long size, String digest, String url ) {
        return """
                {"tag_name":"%s","draft":false,"assets":[
                  {"name":"other.txt","size":1},
                  {"name":"%s","size":%d,"digest":"%s","browser_download_url":"%s","state":"uploaded"}]}"""
                .formatted( tag, name, size, digest, url );
    }
}
