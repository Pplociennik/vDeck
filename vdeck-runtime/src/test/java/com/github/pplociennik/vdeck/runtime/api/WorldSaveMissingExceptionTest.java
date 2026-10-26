package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

/** Verifies missing-save diagnostics preserve their technical cause and reject absent causes. */
class WorldSaveMissingExceptionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private WorldSaveMissingException underTest;

    private IOException cause;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates an isolated missing-source diagnostic. */
    @BeforeEach
    void setUp() {
        cause = new NoSuchFileException( "Dedicated.db" );
        underTest = new WorldSaveMissingException( cause );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** Keeps the original file lookup cause without embedding its path in the summary. */
    @Test
    void shouldRetainCause_whenWorldSaveIsMissing() {
        // GIVEN
        // A required source file is absent.
        // WHEN
        IOException result = underTest.getCause();
        // THEN
        assertThat( result ).isSameAs( cause );
        assertThat( underTest.getMessage() ).isEqualTo( "Required world save is missing" );
    }

    /** Missing causes cannot create an ambiguous failure diagnostic. */
    @Test
    void shouldRejectCause_whenItIsNull() {
        // GIVEN
        // No source diagnostic.
        // WHEN
        var failure = catchThrowable( () -> new WorldSaveMissingException( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "cause" );
    }
}
