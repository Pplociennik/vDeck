package com.github.pplociennik.vdeck.feature.logs.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the validation and the factory of {@link LogChunk}.
 */
class LogChunkTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LogChunk underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the chunk of a panel without any launch log.
     */
    @BeforeEach
    void setUp() {
        underTest = LogChunk.none();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a chunk without text is rejected, so the browser never receives a null it would append.
     */
    @Test
    void shouldThrowException_whenTextIsNull() {
        // GIVEN
        // a chunk without text

        // WHEN
        var exception = catchThrowable( () -> new LogChunk( "run.log", 0, null, false ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "text" );
    }

    /**
     * Verifies that the chunk of a panel without a launch log has no source and no text and tells the
     * browser to clear the console.
     */
    @Test
    void shouldClearConsoleWithoutSource_whenNoLogExists() {
        // GIVEN
        // the chunk created for a panel without any launch log

        // WHEN
        String source = underTest.source();

        // THEN
        assertThat( source ).isNull();
        assertThat( underTest.offset() ).isZero();
        assertThat( underTest.text() ).isEmpty();
        assertThat( underTest.reset() ).isTrue();
    }
}
