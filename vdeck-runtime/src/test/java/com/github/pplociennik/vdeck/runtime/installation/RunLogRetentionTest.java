package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the 30-day retention of run logs.
 */
class RunLogRetentionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDate TODAY = LocalDate.of( 2026, 10, 27 );

    @TempDir
    private Path directory;

    private Path installation;

    private Path logs;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the launcher log directory of a canonical installation.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        logs = Files.createDirectories( installation.resolve( ".vDeck/logs/launcher" ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that day directories older than 30 days are removed and the last 30 days are kept.
     */
    @Test
    void shouldDeleteOnlyExpiredDays_whenRetentionRuns() throws Exception {
        // GIVEN
        Path expired = day( "2026-09-27" );
        Path oldestKept = day( "2026-09-28" );
        Path today = day( "2026-10-27" );

        // WHEN
        int deleted = RunLogRetention.deleteExpired( installation, "launcher", TODAY );

        // THEN
        assertThat( deleted ).isEqualTo( 1 );
        assertThat( expired ).doesNotExist();
        assertThat( oldestKept ).exists();
        assertThat( today ).exists();
    }

    /**
     * Verifies that directories not named after a date are never deleted.
     */
    @Test
    void shouldKeepForeignDirectory_whenNameIsNoDate() throws Exception {
        // GIVEN
        Path foreign = day( "keep-me" );

        // WHEN
        int deleted = RunLogRetention.deleteExpired( installation, "launcher", TODAY );

        // THEN
        assertThat( deleted ).isZero();
        assertThat( foreign ).exists();
    }

    /**
     * Verifies that a component without logs is not an error.
     */
    @Test
    void shouldDeleteNothing_whenComponentHasNoLogs() {
        // GIVEN
        // The updater has no log directory.

        // WHEN
        int deleted = RunLogRetention.deleteExpired( installation, "updater", TODAY );

        // THEN
        assertThat( deleted ).isZero();
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> RunLogRetention.deleteExpired( installation, "launcher", null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private Path day( String name ) throws Exception {
        Path day = Files.createDirectory( logs.resolve( name ) );
        Files.writeString( day.resolve( "run.log" ), "entry" );
        return day;
    }
}
