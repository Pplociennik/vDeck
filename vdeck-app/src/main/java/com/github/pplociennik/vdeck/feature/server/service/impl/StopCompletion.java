package com.github.pplociennik.vdeck.feature.server.service.impl;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Requests a runtime stop operation on behalf of a panel close strategy and blocks until it ends. Panel
 * shutdown must go on whatever happens to the server, so every outcome — rejection, failure, timeout or
 * interruption — is logged and reported as {@code false} instead of thrown.
 */
@Log4j2
final class StopCompletion {

    /**
     * Longest wait for one runtime operation, in minutes. It exceeds the runtime's own five-minute stop
     * deadline, so the runtime normally ends a hanging operation first; the wait only guards against a
     * completion that is never delivered. On expiry the operation is abandoned and the panel closes.
     */
    static final Duration MAX_WAIT = Duration.ofMinutes( 6 );

    private StopCompletion() {
    }

    /**
     * Requests the operation and waits for its completion. The calling thread is blocked for at most the
     * given timeout; an interruption ends the wait and restores the interrupt flag.
     *
     * @param operation
     *         name of the operation used in log messages
     * @param request
     *         call admitting the operation to the runtime; may throw when the runtime refuses it
     * @param timeout
     *         longest wait for the completion
     * @return {@code true} when the operation completed normally, {@code false} when it was refused, failed,
     *         timed out or the wait was interrupted
     * @throws NullPointerException
     *         if an argument is null
     */
    static boolean await( @Nonnull String operation, @Nonnull Supplier< CompletionStage< Void > > request,
                          @Nonnull Duration timeout ) {
        requireNonNull( operation, "operation must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( timeout, "timeout must not be null" );

        try {
            request.get().toCompletableFuture().get( timeout.toNanos(), TimeUnit.NANOSECONDS );
            log.info( "{} completed during panel shutdown", operation );
            return true;
        } catch ( RuntimeException exception ) {
            log.warn( "{} was refused during panel shutdown", operation, exception );
        } catch ( ExecutionException exception ) {
            log.warn( "{} failed during panel shutdown", operation, exception.getCause() );
        } catch ( TimeoutException exception ) {
            log.warn( "{} did not complete within {} during panel shutdown", operation, timeout );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            log.warn( "Waiting for {} was interrupted during panel shutdown", operation );
        }
        return false;
    }
}
