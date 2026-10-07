package com.github.pplociennik.vdeck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Verifies the observable contracts of the server-stop implementation in isolation. */
class ServerShutdownLifecycleTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private ServerShutdownLifecycle underTest;

    private ServerRuntime runtime;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a fresh isolated fixture. */
    @BeforeEach
    void setUp() throws Exception {
        runtime = mock( ServerRuntime.class );
        underTest = new ServerShutdownLifecycle( runtime );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================


    /** Waits for runtime completion and calls the Spring callback exactly once. */
    @Test
    void shouldCompleteCallback_whenGracefulStopCompletes() throws Exception {
        // GIVEN
        var completion = new CompletableFuture< Void >();
        var calls = new AtomicInteger();
        given( runtime.shutdown() ).willReturn( completion );
        underTest.start();
        // WHEN
        underTest.stop( calls::incrementAndGet );
        completion.complete( null );
        // THEN
        assertThat( calls ).hasValue( 1 );
        assertThat( underTest.isRunning() ).isFalse();
        verify( runtime ).shutdown();
    }

    /** Completes Spring shutdown after failure without creating force permission. */
    @Test
    void shouldCompleteCallback_whenRuntimeStopFails() throws Exception {
        // GIVEN
        var calls = new AtomicInteger();
        given( runtime.shutdown() ).willReturn(
                CompletableFuture.failedFuture( new IllegalStateException( "Stop failed" ) ) );
        // WHEN
        underTest.stop( calls::incrementAndGet );
        // THEN
        assertThat( calls ).hasValue( 1 );
    }

    /** Completes the callback if shutdown cannot even be admitted. */
    @Test
    void shouldCompleteCallback_whenRuntimeRejectsShutdown() throws Exception {
        // GIVEN
        var calls = new AtomicInteger();
        given( runtime.shutdown() ).willThrow( new IllegalStateException( "Unavailable" ) );
        // WHEN
        underTest.stop( calls::incrementAndGet );
        // THEN
        assertThat( calls ).hasValue( 1 );
    }

    /** Does not invoke a throwing callback a second time. */
    @Test
    void shouldInvokeCallbackOnce_whenCallbackThrows() throws Exception {
        // GIVEN
        var calls = new AtomicInteger();
        given( runtime.shutdown() ).willReturn( CompletableFuture.completedFuture( null ) );
        // WHEN
        underTest.stop( () -> {
            calls.incrementAndGet();
            throw new IllegalStateException( "Synthetic callback failure" );
        } );
        // THEN
        assertThat( calls ).hasValue( 1 );
    }

    /** Rejects a null callback before admitting shutdown. */
    @Test
    void shouldRejectNull_whenCallbackIsMissing() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> underTest.stop( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    /** Requires the runtime dependency for every lifecycle operation. */
    @Test
    void shouldRejectNull_whenRuntimeIsMissing() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> new ServerShutdownLifecycle( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
