package com.github.pplociennik.vdeck.feature.logs;

import com.github.pplociennik.vdeck.feature.logs.dto.LogChunk;
import com.github.pplociennik.vdeck.feature.logs.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies MVC behavior of {@link ConsoleController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class ConsoleControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private LogService logService;

    private ConsoleController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated log service.
     */
    @BeforeEach
    void setUp() {
        underTest = new ConsoleController( logService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the controller cannot be created without the service reading the log.
     */
    @Test
    void shouldThrowException_whenLogServiceIsNull() {
        // GIVEN
        // no log service

        // WHEN
        var exception = catchThrowable( () -> new ConsoleController( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "logService" );
    }

    /**
     * Verifies that opening the console resolves to its template, whose content console.js loads.
     */
    @Test
    void shouldReturnConsoleTemplate_whenConsoleIsOpened() {
        // GIVEN
        // an administrator opening the console

        // WHEN
        String result = underTest.showConsole();

        // THEN
        assertThat( result ).isEqualTo( "logs/console" );
    }

    /**
     * Verifies that a poll returns the chunk the service read from the requested position.
     */
    @Test
    void shouldReturnChunkFromService_whenLogIsPolled() {
        // GIVEN
        LogChunk chunk = new LogChunk( "run.log", 12, "line\n", false );
        given( logService.readCurrent( 7L ) ).willReturn( chunk );

        // WHEN
        LogChunk result = underTest.readLog( 7L );

        // THEN
        assertThat( result ).isEqualTo( chunk );
    }

    /**
     * Verifies that the first poll, which has no position yet, is passed on without one.
     */
    @Test
    void shouldPassMissingOffset_whenFirstPollHasNoPosition() {
        // GIVEN
        LogChunk chunk = LogChunk.none();
        given( logService.readCurrent( null ) ).willReturn( chunk );

        // WHEN
        LogChunk result = underTest.readLog( null );

        // THEN
        assertThat( result ).isEqualTo( chunk );
    }
}
