package com.github.pplociennik.vdeck.feature.releasenotes.exc;

import com.github.pplociennik.vdeck.feature.releasenotes.ReleaseNotesMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link ReleaseNotesException} keeps its cause and carries only the message key.
 */
class ReleaseNotesExceptionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private final IllegalStateException cause = new IllegalStateException( "database locked" );

    private ReleaseNotesException underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the exception of a failed acknowledgement.
     */
    @BeforeEach
    void setUp() {
        underTest = ReleaseNotesException.acknowledgementFailed( cause );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the failure keeps its cause for the advice's log entry and names the message to show.
     */
    @Test
    void shouldKeepCauseAndKey_whenAcknowledgementFails() {
        // GIVEN
        // the exception of a failed acknowledgement

        // WHEN
        var result = underTest.getMessageKey();

        // THEN
        assertThat( result ).isEqualTo( ReleaseNotesMessage.ACKNOWLEDGEMENT_FAILED );
        assertThat( underTest ).hasCause( cause ).hasMessageContaining( "ACKNOWLEDGEMENT_FAILED" );
    }

    /**
     * Verifies that a failure cannot be created without its cause.
     */
    @Test
    void shouldThrowException_whenCauseIsNull() {
        // GIVEN
        // no cause

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesException.acknowledgementFailed( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "cause" );
    }
}
