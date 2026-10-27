package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.UpdatePreparationException;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies with a real HTTP client against a loopback server that a response whose body stops arriving
 * after the headers is aborted, the API response by its own limit and the package download by its stall
 * timeout or the preparation ceiling, that nothing is published afterwards, and that a slow but steady
 * package body is not aborted. No external network is used.
 */
class StalledResponseIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final byte[] SLOW_BODY = "slow!".getBytes( StandardCharsets.US_ASCII );

    private static final Duration SLOW_PAUSE = Duration.ofMillis( 150 );

    @TempDir
    private Path directory;

    private HttpServer server;

    private CountDownLatch release;

    private HttpClient client;

    private UpdateLayout layout;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Starts a loopback server that sends headers and a few bytes, then stalls until the test ends.
     */
    @BeforeEach
    void setUp() throws Exception {
        release = new CountDownLatch( 1 );
        server = HttpServer.create( new InetSocketAddress( InetAddress.getLoopbackAddress(), 0 ), 0 );
        server.createContext( "/", exchange -> {
            exchange.sendResponseHeaders( 200, 1_000_000 );
            try ( OutputStream body = exchange.getResponseBody() ) {
                body.write( "{\"tag".getBytes() );
                body.flush();
                release.await();
            } catch ( InterruptedException | IOException exception ) {
                Thread.currentThread().interrupt();
            }
        } );
        server.createContext( "/slow", exchange -> {
            exchange.sendResponseHeaders( 200, SLOW_BODY.length );
            try ( OutputStream body = exchange.getResponseBody() ) {
                for ( byte value : SLOW_BODY ) {
                    Thread.sleep( SLOW_PAUSE );
                    body.write( value );
                    body.flush();
                }
            } catch ( InterruptedException exception ) {
                Thread.currentThread().interrupt();
            }
        } );
        server.start();
        client = HttpClient.newBuilder().connectTimeout( Duration.ofSeconds( 5 ) ).build();
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Releases the stalled handlers and stops the server and client.
     */
    @AfterEach
    void tearDown() {
        release.countDown();
        server.stop( 0 );
        client.shutdownNow();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a stalled API body ends with a timeout instead of blocking the start.
     */
    @Test
    void shouldAbortApiRead_whenBodyStalls() {
        // GIVEN
        GitHubReleaseClient releases = new GitHubReleaseClient( client, uri( "/latest" ), new ReleaseVersion( 1, 0, 0 ) );
        Deadline deadline = Deadline.after( Duration.ofMillis( 500 ) );

        // WHEN
        long started = System.nanoTime();
        var failure = catchThrowable( () -> releases.fetchLatest( deadline ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( Duration.ofNanos( System.nanoTime() - started ) ).isLessThan( Duration.ofSeconds( 5 ) );
    }

    /**
     * Verifies that a package body without data for the stall timeout is aborted long before the
     * preparation ceiling and publishes nothing.
     */
    @Test
    void shouldAbortDownload_whenNoDataArrives() throws Exception {
        // GIVEN
        ReleaseDownloader downloader = new ReleaseDownloader( client, mock( UserConsole.class ), Duration.ofMillis( 300 ),
                Duration.ofDays( 1 ) );
        Path download = Files.createDirectories( layout.downloadDirectory() );

        // WHEN
        long started = System.nanoTime();
        var failure = catchThrowable( () -> downloader.download( stalledAsset(), download,
                Deadline.after( Duration.ofMinutes( 1 ) ) ) );

        // THEN
        assertThat( failure ).isInstanceOf( InterruptedIOException.class ).hasMessageContaining( "stalled" );
        assertThat( Duration.ofNanos( System.nanoTime() - started ) ).isLessThan( Duration.ofSeconds( 5 ) );
        assertThat( download ).isEmptyDirectory();
    }

    /**
     * Verifies that a slow package body which keeps delivering data finishes although it takes longer
     * than the stall timeout.
     */
    @Test
    void shouldPublishPackage_whenSlowBodyKeepsArriving() throws Exception {
        // GIVEN
        ReleaseDownloader downloader = new ReleaseDownloader( client, mock( UserConsole.class ), Duration.ofMillis( 300 ),
                Duration.ofDays( 1 ) );
        Path download = Files.createDirectories( layout.downloadDirectory() );
        ReleaseAsset asset = new ReleaseAsset( new ReleaseVersion( 1, 1, 0 ), "vDeck-dist-1.1.0.zip", SLOW_BODY.length,
                HexFormat.of().formatHex( DurableFiles.newDigest().digest( SLOW_BODY ) ), uri( "/slow" ) );

        // WHEN
        long started = System.nanoTime();
        Path zip = downloader.download( asset, download, Deadline.after( Duration.ofMinutes( 1 ) ) );

        // THEN
        assertThat( Duration.ofNanos( System.nanoTime() - started ) ).isGreaterThan( Duration.ofMillis( 300 ) );
        assertThat( zip ).hasBinaryContent( SLOW_BODY );
    }

    /**
     * Verifies that a download still running at the preparation ceiling is cancelled, the worker stops and
     * neither a package nor a staged directory is published.
     */
    @Test
    void shouldCancelDownload_whenCeilingRunsOut() throws Exception {
        // GIVEN
        GitHubReleaseClient releases = mock( GitHubReleaseClient.class );
        given( releases.fetchLatest( any() ) ).willReturn( Optional.of( stalledAsset() ) );
        UpdatePreparation preparation = new UpdatePreparation( layout, mock( UserConsole.class ), client, releases,
                new ReleaseDownloader( client, mock( UserConsole.class ), Duration.ofMinutes( 1 ), Duration.ofDays( 1 ) ),
                new SafeZipExtractor(), new UpdateExecutorPreparation(), Duration.ofMillis( 700 ) );

        // WHEN
        long started = System.nanoTime();
        var failure = catchThrowable( () -> preparation.prepare( new ReleaseVersion( 1, 0, 0 ) ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying( UpdatePreparationException.class,
                exception -> assertThat( exception.isWorkerStopped() ).isTrue() );
        assertThat( Duration.ofNanos( System.nanoTime() - started ) ).isLessThan( Duration.ofSeconds( 10 ) );
        assertThat( layout.downloadDirectory().resolve( "vDeck-dist-1.1.0.zip" ) ).doesNotExist();
        assertThat( layout.stagedDirectory() ).doesNotExist();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private ReleaseAsset stalledAsset() {
        return new ReleaseAsset( new ReleaseVersion( 1, 1, 0 ), "vDeck-dist-1.1.0.zip", 1_000_000, "ab".repeat( 32 ),
                uri( "/package.zip" ) );
    }

    private URI uri( String path ) {
        return URI.create( "http://127.0.0.1:" + server.getAddress().getPort() + path );
    }
}
