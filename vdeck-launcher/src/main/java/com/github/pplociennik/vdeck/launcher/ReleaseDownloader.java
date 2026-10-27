package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
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
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 * <p>A slow connection may take as long as it keeps delivering data (AD-027): a watchdog closes the body
 * only when no byte arrives for the stall timeout, which also aborts a read blocked on a stalled
 * connection. The shared deadline, the safety ceiling of the whole preparation, is checked between reads
 * and before publication. Progress is shown in the console at a fixed interval.
 */
@Log4j2
final class ReleaseDownloader {

    /**
     * Longest time without any received byte before the download is aborted.
     */
    static final Duration STALL_TIMEOUT = Duration.ofSeconds( 30 );

    /**
     * Interval between progress messages in the console.
     */
    static final Duration PROGRESS_INTERVAL = Duration.ofSeconds( 10 );

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds( 30 );

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final double MEBIBYTE = 1024 * 1024;

    private static final Locale POLISH = Locale.forLanguageTag( "pl" );

    private final HttpClient client;

    private final UserConsole console;

    private final Duration stallTimeout;

    private final Duration progressInterval;

    /**
     * Creates the downloader.
     *
     * @param client
     *         HTTP client following redirects without HTTPS-to-HTTP downgrades
     * @param console
     *         user console showing the download progress
     * @param stallTimeout
     *         longest time without data, {@link #STALL_TIMEOUT} outside tests
     * @param progressInterval
     *         interval between progress messages, {@link #PROGRESS_INTERVAL} outside tests
     */
    ReleaseDownloader( @Nonnull HttpClient client, @Nonnull UserConsole console, @Nonnull Duration stallTimeout,
                       @Nonnull Duration progressInterval ) {
        this.client = requireNonNull( client, "client must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
        this.stallTimeout = requireNonNull( stallTimeout, "stallTimeout must not be null" );
        this.progressInterval = requireNonNull( progressInterval, "progressInterval must not be null" );
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
     *         if the download fails, stalls, exceeds the declared size, is truncated, has another digest or
     *         the deadline expires
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

    private String copy( InputStream body, Path part, long expectedSize, Deadline deadline ) throws IOException {
        MessageDigest digest = DurableFiles.newDigest();
        long total = 0;
        long started = System.nanoTime();
        long reported = started;
        try ( StallWatchdog watchdog = new StallWatchdog( body, stallTimeout );
              FileChannel output = FileChannel.open( part, CREATE_NEW, WRITE, NOFOLLOW_LINKS ) ) {
            byte[] buffer = new byte[ BUFFER_SIZE ];
            try {
                for ( int read = body.read( buffer ); read >= 0; read = body.read( buffer ) ) {
                    watchdog.progress();
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
                    long now = System.nanoTime();
                    if ( now - reported >= progressInterval.toNanos() ) {
                        console.show( progress( total, expectedSize, now - started ) );
                        reported = now;
                    }
                }
            } catch ( IOException exception ) {
                throw watchdog.hasFired() ? stalled() : exception;
            }
            // A body closed by the watchdog may also end as a premature end of stream.
            if ( watchdog.hasFired() ) {
                throw stalled();
            }
            output.force( true );
        }
        if ( total != expectedSize ) {
            throw new IOException( "The download is shorter than the declared size" );
        }
        return HexFormat.of().formatHex( digest.digest() );
    }

    private InterruptedIOException stalled() {
        return new InterruptedIOException( "The download stalled: no data for " + stallTimeout );
    }

    private static String progress( long received, long expected, long elapsedNanos ) {
        double seconds = Math.max( elapsedNanos, 1 ) / 1_000_000_000.0;
        return String.format( POLISH, "Pobieranie: %.1f z %.1f MB (%d%%), %.1f MB/s", received / MEBIBYTE,
                expected / MEBIBYTE, received * 100 / expected, received / MEBIBYTE / seconds );
    }

    private static Duration positive( Duration duration ) throws InterruptedIOException {
        if ( duration.isZero() ) {
            throw new InterruptedIOException( "Update preparation deadline expired" );
        }
        return duration;
    }

    /**
     * Closes the body when no data has arrived for the timeout. Each check reschedules itself for the
     * moment the current idle period would end, so a steady download costs one timer task per timeout.
     */
    private static final class StallWatchdog implements AutoCloseable {

        private final InputStream body;

        private final long timeoutNanos;

        private final ScheduledExecutorService timer;

        private volatile long lastProgress;

        private volatile boolean fired;

        private StallWatchdog( InputStream body, Duration timeout ) {
            this.body = body;
            this.timeoutNanos = timeout.toNanos();
            this.lastProgress = System.nanoTime();
            this.timer = Executors.newSingleThreadScheduledExecutor( runnable -> {
                Thread thread = new Thread( runnable, "vdeck-download-watchdog" );
                thread.setDaemon( true );
                return thread;
            } );
            schedule( timeoutNanos );
        }

        @Override
        public void close() {
            timer.shutdownNow();
        }

        private void progress() {
            lastProgress = System.nanoTime();
        }

        private boolean hasFired() {
            return fired;
        }

        private void schedule( long delayNanos ) {
            if ( !timer.isShutdown() ) {
                timer.schedule( this::check, delayNanos, TimeUnit.NANOSECONDS );
            }
        }

        private void check() {
            long idle = System.nanoTime() - lastProgress;
            if ( idle < timeoutNanos ) {
                schedule( timeoutNanos - idle );
                return;
            }
            log.warn( "No data received for {} ms; aborting the download", TimeUnit.NANOSECONDS.toMillis( idle ) );
            fired = true;
            try {
                body.close();
            } catch ( IOException exception ) {
                log.debug( "Could not close a stalled download", exception );
            }
        }
    }
}
