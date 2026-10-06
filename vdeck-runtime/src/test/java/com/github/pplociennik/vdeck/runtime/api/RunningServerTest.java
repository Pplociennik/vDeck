package com.github.pplociennik.vdeck.runtime.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that a managed process is described completely and with an absolute log file, so nothing
 * downstream has to guess where the server is writing.
 */
@ExtendWith( MockitoExtension.class )
class RunningServerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Instant STARTED_AT = Instant.parse( "2026-09-28T21:43:07Z" );

    private static final Path LOG_FILE = Path.of( "C:\\Valheim\\.vDeck\\log\\run.log" ).toAbsolutePath();

    @Mock
    private ProcessHandle processHandle;

    private RunningServer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Describes a managed process backed by a stand-in handle.
     */
    @BeforeEach
    void setUp() {
        underTest = new RunningServer( processHandle, STARTED_AT, LOG_FILE );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every component is kept, so the lifecycle layer can report the process without
     * looking it up again.
     */
    @Test
    void shouldKeepEveryComponent_whenBuilt() {
        // GIVEN
        // a described managed process

        // WHEN
        ProcessHandle result = underTest.handle();

        // THEN
        assertThat( result ).isSameAs( processHandle );
        assertThat( underTest.startedAt() ).isEqualTo( STARTED_AT );
        assertThat( underTest.logFile() ).isEqualTo( LOG_FILE );
    }

    /**
     * Verifies that a relative log file is refused, because the server resolves it against its own
     * working directory rather than the panel's.
     */
    @Test
    void shouldThrowException_whenLogFileIsRelative() {
        // GIVEN
        Path relative = Path.of( "run.log" );

        // WHEN
        Throwable thrown = catchThrowable( () -> new RunningServer( processHandle, STARTED_AT, relative ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "logFile must be absolute" );
    }

    /**
     * Verifies that a process cannot be described without its handle, which is the only thing keeping
     * the panel's grip on it.
     */
    @Test
    void shouldThrowException_whenHandleIsNull() {
        // GIVEN
        // no process handle

        // WHEN
        Throwable thrown = catchThrowable( () -> new RunningServer( null, STARTED_AT, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "handle must not be null" );
    }

    /**
     * Verifies that the moment of creation is required, since it is what the status reports as the
     * server's uptime origin.
     */
    @Test
    void shouldThrowException_whenStartInstantIsNull() {
        // GIVEN
        // no start instant

        // WHEN
        Throwable thrown = catchThrowable( () -> new RunningServer( processHandle, null, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "startedAt must not be null" );
    }

    /**
     * Verifies that the log file is required, so no launch can exist without a place to read its
     * output from.
     */
    @Test
    void shouldThrowException_whenLogFileIsNull() {
        // GIVEN
        // no log file

        // WHEN
        Throwable thrown = catchThrowable( () -> new RunningServer( processHandle, STARTED_AT, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "logFile must not be null" );
    }
}