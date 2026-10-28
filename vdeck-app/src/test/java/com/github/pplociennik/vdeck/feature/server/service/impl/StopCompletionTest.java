package com.github.pplociennik.vdeck.feature.server.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link StopCompletion} reports every outcome of a runtime stop operation without throwing.
 * The class is a static utility, so the tests call it directly and need no {@code underTest} instance.
 */
class StopCompletionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Duration WAIT = Duration.ofSeconds( 1 );

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Clears an interrupt flag left by the interruption test, so it cannot leak into another test.
     */
    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an operation completing normally is reported as successful.
     */
    @Test
    void shouldReturnTrue_whenOperationCompletes() {
        // GIVEN
        Supplier< CompletionStage< Void > > request = () -> CompletableFuture.completedFuture( null );

        // WHEN
        boolean result = StopCompletion.await( "Stop", request, WAIT );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that an operation completing exceptionally is reported as failed instead of thrown.
     */
    @Test
    void shouldReturnFalse_whenOperationFails() {
        // GIVEN
        Supplier< CompletionStage< Void > > request =
                () -> CompletableFuture.failedFuture( new IllegalStateException( "Stop failed" ) );

        // WHEN
        boolean result = StopCompletion.await( "Stop", request, WAIT );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that an operation the runtime refuses to admit is reported as failed instead of thrown.
     */
    @Test
    void shouldReturnFalse_whenRuntimeRefusesOperation() {
        // GIVEN
        Supplier< CompletionStage< Void > > request = () -> {
            throw new IllegalStateException( "Stop request is stale" );
        };

        // WHEN
        boolean result = StopCompletion.await( "Stop", request, WAIT );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that an operation still running when the wait expires is abandoned and reported as failed.
     */
    @Test
    void shouldReturnFalse_whenOperationDoesNotCompleteInTime() {
        // GIVEN
        Supplier< CompletionStage< Void > > request = CompletableFuture::new;

        // WHEN
        boolean result = StopCompletion.await( "Stop", request, Duration.ZERO );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that an interrupted wait is reported as failed and keeps the thread's interrupt flag set.
     */
    @Test
    void shouldRestoreInterruptFlag_whenWaitIsInterrupted() {
        // GIVEN
        Supplier< CompletionStage< Void > > request = CompletableFuture::new;
        Thread.currentThread().interrupt();

        // WHEN
        boolean result = StopCompletion.await( "Stop", request, WAIT );

        // THEN
        assertThat( result ).isFalse();
        assertThat( Thread.currentThread().isInterrupted() ).isTrue();
    }

    /**
     * Verifies that a missing argument is rejected before the operation is requested.
     */
    @Test
    void shouldRejectNull_whenArgumentIsMissing() {
        // GIVEN
        Supplier< CompletionStage< Void > > request = () -> CompletableFuture.completedFuture( null );

        // WHEN
        var missingOperation = catchThrowable( () -> StopCompletion.await( null, request, WAIT ) );
        var missingRequest = catchThrowable( () -> StopCompletion.await( "Stop", null, WAIT ) );
        var missingTimeout = catchThrowable( () -> StopCompletion.await( "Stop", request, null ) );

        // THEN
        assertThat( missingOperation ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "operation" );
        assertThat( missingRequest ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "request" );
        assertThat( missingTimeout ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "timeout" );
    }
}
