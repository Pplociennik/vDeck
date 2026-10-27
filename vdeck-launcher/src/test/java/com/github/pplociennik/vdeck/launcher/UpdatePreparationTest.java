package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.UpdatePreparationException;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * Verifies the order of the preparation steps and its deadline with stubbed steps.
 */
@ExtendWith( MockitoExtension.class )
class UpdatePreparationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion INSTALLED = new ReleaseVersion( 1, 1, 0 );

    private static final ReleaseVersion NEWER = new ReleaseVersion( 1, 2, 0 );

    @TempDir
    private Path directory;

    @Mock
    private UserConsole console;

    @Mock
    private HttpClient client;

    @Mock
    private GitHubReleaseClient releases;

    @Mock
    private ReleaseDownloader downloader;

    @Mock
    private SafeZipExtractor extractor;

    @Mock
    private UpdateExecutorPreparation executor;

    private UpdateLayout layout;

    private UpdatePreparation underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the preparation with a one-minute budget.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        underTest = preparation( Duration.ofMinutes( 1 ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a newer release is downloaded, extracted and turned into an executor, in this order.
     */
    @Test
    void shouldPrepareRelease_whenNewerVersionIsPublished() throws Exception {
        // GIVEN
        ReleaseAsset asset = asset( NEWER );
        Path zip = layout.downloadDirectory().resolve( asset.name() );
        given( releases.fetchLatest( any() ) ).willReturn( Optional.of( asset ) );
        given( downloader.download( eq( asset ), eq( layout.downloadDirectory() ), any() ) ).willReturn( zip );

        // WHEN
        Optional< ReleaseVersion > prepared = underTest.prepare( INSTALLED );

        // THEN
        assertThat( prepared ).contains( NEWER );
        then( extractor ).should().extract( eq( zip ), eq( layout ), eq( NEWER ), any() );
        then( executor ).should().prepare( eq( layout ), any() );
        then( client ).should( never() ).shutdownNow();
        then( console ).should().show( "Znaleziono nowszą wersję vDeck 1.2.0. Pobieranie..." );
    }

    /**
     * Verifies that an equal or older release is never installed.
     */
    @Test
    void shouldPrepareNothing_whenLatestIsNotNewer() throws Exception {
        // GIVEN
        given( releases.fetchLatest( any() ) ).willReturn( Optional.of( asset( INSTALLED ) ) );

        // WHEN
        Optional< ReleaseVersion > prepared = underTest.prepare( INSTALLED );

        // THEN
        assertThat( prepared ).isEmpty();
        then( downloader ).should( never() ).download( any(), any(), any() );
        then( console ).should().show( "Brak nowszej wersji. Zainstalowana wersja vDeck 1.1.0 jest aktualna." );
    }

    /**
     * Verifies that a failed step is reported with a stopped worker, so its files may be deleted.
     */
    @Test
    void shouldReportStoppedWorker_whenStepFails() throws Exception {
        // GIVEN
        given( releases.fetchLatest( any() ) ).willReturn( Optional.of( asset( NEWER ) ) );
        willThrow( new IOException( "digest mismatch" ) ).given( downloader ).download( any(), any(), any() );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( INSTALLED ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying( UpdatePreparationException.class,
                exception -> assertThat( exception.isWorkerStopped() ).isTrue() );
        then( extractor ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a blocked step is cancelled when the budget runs out: the HTTP client is shut down,
     * the worker is interrupted and has stopped before the failure is reported.
     */
    @Test
    void shouldCancelBlockedWorker_whenBudgetRunsOut() throws Exception {
        // GIVEN
        CountDownLatch never = new CountDownLatch( 1 );
        given( releases.fetchLatest( any() ) ).willAnswer( invocation -> {
            never.await();
            return Optional.empty();
        } );
        underTest = preparation( Duration.ofMillis( 200 ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( INSTALLED ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying( UpdatePreparationException.class,
                exception -> assertThat( exception.isWorkerStopped() ).isTrue() );
        then( client ).should().shutdownNow();
    }

    /**
     * Verifies that leftovers of an earlier preparation stop a new one instead of being reused.
     */
    @Test
    void shouldFail_whenStagedLeftoversExist() throws Exception {
        // GIVEN
        given( releases.fetchLatest( any() ) ).willReturn( Optional.of( asset( NEWER ) ) );
        Files.createDirectories( layout.stagedDirectory() );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( INSTALLED ) );

        // THEN
        assertThat( failure ).isInstanceOf( UpdatePreparationException.class ).hasRootCauseMessage(
                "Update leftovers were not cleaned up" );
    }

    /**
     * Verifies that a null version is rejected.
     */
    @Test
    void shouldThrowException_whenVersionIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private UpdatePreparation preparation( Duration budget ) {
        return new UpdatePreparation( layout, console, client, releases, downloader, extractor, executor, budget );
    }

    private static ReleaseAsset asset( ReleaseVersion version ) {
        return new ReleaseAsset( version, "vDeck-dist-" + version + ".zip", 10, "ab".repeat( 32 ),
                URI.create( "https://github.com/Pplociennik/vDeck/releases/download/v" + version + "/vDeck-dist-"
                        + version + ".zip" ) );
    }
}
