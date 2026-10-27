package com.github.pplociennik.vdeck.launcher;

import jakarta.annotation.Nonnull;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

/**
 * Monotonic deadline shared by every step of an update preparation (AD-024). It is never renewed by a
 * redirect, a retry or a new step. Checking it between reads alone cannot stop a blocked read, so the
 * owner of the preparation additionally aborts the HTTP client and interrupts the worker when it expires.
 */
final class Deadline {

    private final LongSupplier nanoTime;

    private final long expiresAt;

    private Deadline( LongSupplier nanoTime, long expiresAt ) {
        this.nanoTime = nanoTime;
        this.expiresAt = expiresAt;
    }

    /**
     * Starts a deadline on the system's monotonic clock.
     *
     * @param budget
     *         time available from now
     * @return running deadline
     * @throws NullPointerException
     *         if the budget is null
     */
    static @Nonnull Deadline after( @Nonnull Duration budget ) {
        return after( budget, System::nanoTime );
    }

    /**
     * Starts a deadline on an explicit monotonic clock.
     *
     * @param budget
     *         time available from now
     * @param nanoTime
     *         monotonic clock in nanoseconds
     * @return running deadline
     * @throws NullPointerException
     *         if an argument is null
     */
    static @Nonnull Deadline after( @Nonnull Duration budget, @Nonnull LongSupplier nanoTime ) {
        requireNonNull( budget, "budget must not be null" );
        requireNonNull( nanoTime, "nanoTime must not be null" );
        return new Deadline( nanoTime, nanoTime.getAsLong() + budget.toNanos() );
    }

    /**
     * Returns the time left, never negative.
     *
     * @return remaining time, {@link Duration#ZERO} once expired
     */
    @Nonnull
    Duration remaining() {
        long left = expiresAt - nanoTime.getAsLong();
        return left > 0 ? Duration.ofNanos( left ) : Duration.ZERO;
    }

    /**
     * Returns the shorter of the remaining time and a step's own limit.
     *
     * @param limit
     *         limit of one step
     * @return time available for the step
     * @throws NullPointerException
     *         if the limit is null
     */
    @Nonnull
    Duration remainingAtMost( @Nonnull Duration limit ) {
        Duration remaining = remaining();
        return remaining.compareTo( requireNonNull( limit, "limit must not be null" ) ) < 0 ? remaining : limit;
    }

    /**
     * Fails when the deadline has passed or the current thread was interrupted. Called before every
     * publication, so a late worker never publishes anything.
     *
     * @throws InterruptedIOException
     *         if the deadline expired or the preparation was cancelled
     */
    void check() throws InterruptedIOException {
        if ( Thread.currentThread().isInterrupted() ) {
            throw new InterruptedIOException( "Update preparation was cancelled" );
        }
        if ( remaining().isZero() ) {
            throw new InterruptedIOException( "Update preparation deadline expired" );
        }
    }
}
