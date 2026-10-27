package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Objects.requireNonNull;

/**
 * Downloads a release asset into a {@code .part} file, computing SHA-256 while streaming (AD-024).
 * The package is published under its final name only when its size equals the size declared by the API
 * exactly and its digest matches in a constant-time comparison; otherwise nothing is published.
 *
 * <p>The deadline is checked between reads and before publication. A read blocked on a stalled
 * connection is aborted by the owner of the preparation, which shuts the HTTP client down and interrupts
 * the worker when the deadline expires.
 */
@Log4j2
final class ReleaseDownloader {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds( 30 );

    private static final int BUFFER_SIZE = 64 * 1024;

    private final HttpClient client;

    /**
     * Creates the downloader.
     *
     * @param client
     *         HTTP client following redirects without HTTPS-to-HTTP downgrades
     */
    ReleaseDownloader( @Nonnull HttpClient client ) {
        this.client = requireNonNull( client, "client must not be null" );
    }

    /**
     * Downloads and verifies the asset.
     *
     * @param asset
     *         validated release asset
     * @param directory
     *         existing, checked download directory
     * @param deadline
     *         shared preparation deadline
     * @return verified package file
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the download fails, exceeds the declared size, is truncated, has another digest or the
     *         deadline expires
     */
    @Nonnull
    Path download( @Nonnull ReleaseAsset asset, @Nonnull Path directory, @Nonnull Deadline deadline )
            throws IOException {
        requireNonNull( asset, "asset must not be null" );
        requireNonNull( directory, "directory must not be null" );
        requireNonNull( deadline, "deadline must not be null" );
        Path part = directory.resolve( asset.name() + ".part" );
        Path target = directory.resolve( asset.name() );
        Files.deleteIfExists( part );
        Files.deleteIfExists( target );
        log.info( "Downloading {} ({} bytes)", asset.name(), asset.size() );
        HttpRequest request = HttpRequest.newBuilder( asset.downloadUri() )
                .timeout( positive( deadline.remainingAtMost( RESPONSE_TIMEOUT ) ) ).GET().build();
        HttpResponse< InputStream > response = send( request );
        boolean published = false;
        try ( InputStream body = response.body() ) {
            if ( response.statusCode() != 200 ) {
                throw new IOException( "Download answered with status " + response.statusCode() );
            }
            String digest = copy( body, part, asset.size(), deadline );
            if ( !MessageDigest.isEqual( HexFormat.of().parseHex( asset.sha256() ), HexFormat.of().parseHex( digest ) ) ) {
                throw new IOException( "The downloaded package has another SHA-256 digest" );
            }
            deadline.check();
            Files.move( part, target, ATOMIC_MOVE );
            published = true;
            log.info( "Downloaded and verified {}", asset.name() );
            return target;
        } finally {
            if ( !published ) {
                Files.deleteIfExists( part );
            }
        }
    }

    private HttpResponse< InputStream > send( HttpRequest request ) throws IOException {
        try {
            return client.send( request, HttpResponse.BodyHandlers.ofInputStream() );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException( "Download was cancelled" );
        }
    }

    private static String copy( InputStream body, Path part, long expectedSize, Deadline deadline ) throws IOException {
        MessageDigest digest = DurableFiles.newDigest();
        long total = 0;
        try ( FileChannel output = FileChannel.open( part, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            byte[] buffer = new byte[ BUFFER_SIZE ];
            for ( int read = body.read( buffer ); read >= 0; read = body.read( buffer ) ) {
                deadline.check();
                total += read;
                if ( total > expectedSize ) {
                    throw new IOException( "The download is larger than the declared size" );
                }
                digest.update( buffer, 0, read );
                ByteBuffer chunk = ByteBuffer.wrap( buffer, 0, read );
                while ( chunk.hasRemaining() ) {
                    output.write( chunk );
                }
            }
            output.force( true );
        }
        if ( total != expectedSize ) {
            throw new IOException( "The download is shorter than the declared size" );
        }
        return HexFormat.of().formatHex( digest.digest() );
    }

    private static Duration positive( Duration duration ) throws InterruptedIOException {
        if ( duration.isZero() ) {
            throw new InterruptedIOException( "Update preparation deadline expired" );
        }
        return duration;
    }
}
