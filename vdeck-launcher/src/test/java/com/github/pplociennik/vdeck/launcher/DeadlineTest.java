package com.github.pplociennik.vdeck.launcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the shared monotonic deadline with a controlled clock.
 */
class DeadlineTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private AtomicLong clock;

    private Deadline underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Starts a ten-second deadline on a controlled clock.
     */
    @BeforeEach
    void setUp() {
        clock = new AtomicLong( 1_000 );
        underTest = Deadline.after( Duration.ofSeconds( 10 ), clock::get );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Clears an interruption flag set by a test.
     */
    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the remaining time and the limit of a single step.
     */
    @Test
    void shouldReportRemainingTime_whenRunning() {
        // GIVEN
        clock.addAndGet( Duration.ofSeconds( 4 ).toNanos() );

        // WHEN
        Duration remaining = underTest.remaining();

        // THEN
        assertThat( remaining ).isEqualTo( Duration.ofSeconds( 6 ) );
        assertThat( underTest.remainingAtMost( Duration.ofSeconds( 2 ) ) ).isEqualTo( Duration.ofSeconds( 2 ) );
        assertThat( underTest.remainingAtMost( Duration.ofSeconds( 20 ) ) ).isEqualTo( Duration.ofSeconds( 6 ) );
        assertThatCode( underTest::check ).doesNotThrowAnyException();
    }

    /**
     * Verifies that an expired deadline never reports negative time and fails checks.
     */
    @Test
    void shouldFailCheck_whenExpired() {
        // GIVEN
        clock.addAndGet( Duration.ofSeconds( 11 ).toNanos() );

        // WHEN
        var failure = catchThrowable( underTest::check );

        // THEN
        assertThat( failure ).isInstanceOf( InterruptedIOException.class ).hasMessageContaining( "expired" );
        assertThat( underTest.remaining() ).isZero();
    }

    /**
     * Verifies that a cancelled worker fails its next check even before the deadline.
     */
    @Test
    void shouldFailCheck_whenThreadIsInterrupted() {
        // GIVEN
        Thread.currentThread().interrupt();

        // WHEN
        var failure = catchThrowable( underTest::check );

        // THEN
        assertThat( failure ).isInstanceOf( InterruptedIOException.class ).hasMessageContaining( "cancelled" );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingBudget = catchThrowable( () -> Deadline.after( null ) );
        var missingLimit = catchThrowable( () -> underTest.remainingAtMost( null ) );

        // THEN
        assertThat( missingBudget ).isInstanceOf( NullPointerException.class );
        assertThat( missingLimit ).isInstanceOf( NullPointerException.class );
    }
}
