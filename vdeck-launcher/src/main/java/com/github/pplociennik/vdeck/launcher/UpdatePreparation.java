package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.UpdatePreparationException;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Checks for a newer release and prepares it for the updater within one deadline (AD-024).
 *
 * <pre>
 * latest release (API) -- none / not newer --> nothing to install
 *   |
 *   v
 * download + SHA-256 -> safe extraction to staged -> executor copy -> ready for the updater
 * </pre>
 *
 * <p>All steps run in one worker thread under a single monotonic {@link #BUDGET}. When the budget runs out
 * the caller's thread shuts the HTTP client down, which aborts any blocked read, interrupts the worker and
 * waits until the worker has really finished. Only then does the caller learn of the failure, so a late
 * worker can never publish files or start the updater after the launcher fell back to the current
 * version. The installation itself is never changed here.
 */
@Log4j2
final class UpdatePreparation {

    /**
     * Budget from the first API request until the executor is ready.
     */
    static final Duration BUDGET = Duration.ofMinutes( 5 );

    private static final Duration WORKER_STOP_TIMEOUT = Duration.ofSeconds( 30 );

    private final UpdateLayout layout;

    private final HttpClient client;

    private final GitHubReleaseClient releases;

    private final ReleaseDownloader downloader;

    private final SafeZipExtractor extractor;

    private final UpdateExecutorPreparation executor;

    private final Duration budget;

    /**
     * Creates the preparation.
     *
     * @param layout
     *         update layout of the canonical installation
     * @param client
     *         HTTP client used by the release client and downloader; shut down on timeout
     * @param releases
     *         GitHub release client
     * @param downloader
     *         package downloader
     * @param extractor
     *         package extractor
     * @param executor
     *         executor preparation
     * @param budget
     *         total time available, {@link #BUDGET} outside tests
     */
    UpdatePreparation( @Nonnull UpdateLayout layout, @Nonnull HttpClient client, @Nonnull GitHubReleaseClient releases,
                       @Nonnull ReleaseDownloader downloader, @Nonnull SafeZipExtractor extractor,
                       @Nonnull UpdateExecutorPreparation executor, @Nonnull Duration budget ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
        this.client = requireNonNull( client, "client must not be null" );
        this.releases = requireNonNull( releases, "releases must not be null" );
        this.downloader = requireNonNull( downloader, "downloader must not be null" );
        this.extractor = requireNonNull( extractor, "extractor must not be null" );
        this.executor = requireNonNull( executor, "executor must not be null" );
        this.budget = requireNonNull( budget, "budget must not be null" );
    }

    /**
     * Prepares the newest release when it is newer than the installed version. Blocks for at most the
     * budget plus the time needed to observe the worker's exit.
     *
     * @param installed
     *         installed release version
     * @return prepared version, or an empty optional when no newer release exists
     * @throws NullPointerException
     *         if the version is null
     * @throws UpdatePreparationException
     *         if a step fails or the budget runs out; {@link UpdatePreparationException#isWorkerStopped()}
     *         tells whether work files may be deleted
     */
    @Nonnull
    Optional< ReleaseVersion > prepare( @Nonnull ReleaseVersion installed ) {
        requireNonNull( installed, "installed must not be null" );
        ExecutorService worker = Executors.newSingleThreadExecutor( runnable -> {
            Thread thread = new Thread( runnable, "vdeck-update-preparation" );
            thread.setDaemon( true );
            return thread;
        } );
        Deadline deadline = Deadline.after( budget );
        Future< Optional< ReleaseVersion > > result = worker.submit( () -> work( installed, deadline ) );
        try {
            return result.get( budget.toNanos(), TimeUnit.NANOSECONDS );
        } catch ( TimeoutException exception ) {
            log.warn( "Update preparation exceeded its budget of {}; cancelling", budget );
            throw cancel( result, worker, exception );
        } catch ( ExecutionException exception ) {
            log.warn( "Update preparation failed", exception.getCause() );
            throw new UpdatePreparationException( exception.getCause(), stop( worker ) );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw cancel( result, worker, exception );
        } finally {
            worker.shutdownNow();
        }
    }

    private Optional< ReleaseVersion > work( ReleaseVersion installed, Deadline deadline ) throws IOException {
        Optional< ReleaseAsset > latest = releases.fetchLatest( deadline );
        if ( latest.isEmpty() || !latest.get().version().isNewerThan( installed ) ) {
            log.info( "No newer release than {} is available", installed );
            return Optional.empty();
        }
        ReleaseAsset asset = latest.get();
        Path download = prepareDirectory( layout.downloadDirectory() );
        Path zip = downloader.download( asset, download, deadline );
        extractor.extract( zip, layout, asset.version(), deadline );
        executor.prepare( layout, deadline );
        deadline.check();
        log.info( "Release {} is ready for the updater", asset.version() );
        return Optional.of( asset.version() );
    }

    private Path prepareDirectory( Path directory ) throws IOException {
        FileWorldSnapshotStore.checkDirectory( layout.installation(), layout.installation() );
        Files.createDirectories( directory );
        FileWorldSnapshotStore.checkDirectory( layout.installation(), directory );
        if ( Files.exists( layout.stagedDirectory(), NOFOLLOW_LINKS )
                || Files.exists( layout.executorDirectory(), NOFOLLOW_LINKS ) ) {
            throw new IOException( "Update leftovers were not cleaned up" );
        }
        return directory;
    }

    private UpdatePreparationException cancel( Future< ? > result, ExecutorService worker, Exception cause ) {
        client.shutdownNow();
        result.cancel( true );
        return new UpdatePreparationException( cause, stop( worker ) );
    }

    private static boolean stop( ExecutorService worker ) {
        worker.shutdownNow();
        try {
            boolean stopped = worker.awaitTermination( WORKER_STOP_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS );
            if ( !stopped ) {
                log.error( "The update preparation worker did not stop; work files are kept" );
            }
            return stopped;
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
