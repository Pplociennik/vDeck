package com.github.pplociennik.vdeck.feature.server.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link ServerLogRetention} removes only the day directories older than the retention
 * window, on an isolated temporary log directory.
 */
class ServerLogRetentionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDate OLDEST_KEPT = LocalDate.of( 2026, 9, 22 );

    @TempDir
    private Path logDirectory;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a day directory dated before the oldest kept day is removed with the logs inside it.
     *
     * @throws IOException
     *         if the day directory cannot be prepared
     */
    @Test
    void shouldRemoveDay_whenItIsOlderThanOldestKept() throws IOException {
        // GIVEN
        Path expired = givenDayDirectory( OLDEST_KEPT.minusDays( 1 ) );

        // WHEN
        ServerLogRetention.removeDaysBefore( logDirectory, OLDEST_KEPT );

        // THEN
        assertThat( expired ).doesNotExist();
    }

    /**
     * Verifies that the oldest kept day itself and later days survive, so the window is not one day
     * shorter than configured.
     *
     * @throws IOException
     *         if the day directories cannot be prepared
     */
    @Test
    void shouldKeepDays_whenTheyAreNotOlderThanOldestKept() throws IOException {
        // GIVEN
        Path boundary = givenDayDirectory( OLDEST_KEPT );
        Path recent = givenDayDirectory( OLDEST_KEPT.plusDays( 10 ) );

        // WHEN
        ServerLogRetention.removeDaysBefore( logDirectory, OLDEST_KEPT );

        // THEN
        assertThat( boundary.resolve( "run.log" ) ).exists();
        assertThat( recent.resolve( "run.log" ) ).exists();
    }

    /**
     * Verifies that directories not named after a date and plain files are left alone, because they do
     * not belong to the retention policy.
     *
     * @throws IOException
     *         if the foreign entries cannot be prepared
     */
    @Test
    void shouldIgnoreForeignEntries_whenRetentionIsApplied() throws IOException {
        // GIVEN
        Path foreignDirectory = Files.createDirectories( logDirectory.resolve( "archive" ) );
        Path fileNamedAsDay = Files.createFile( logDirectory.resolve( "2020-01-01" ) );

        // WHEN
        ServerLogRetention.removeDaysBefore( logDirectory, OLDEST_KEPT );

        // THEN
        assertThat( foreignDirectory ).isDirectory();
        assertThat( fileNamedAsDay ).isRegularFile();
    }

    /**
     * Verifies that a log directory that cannot be listed is only logged, because a cleanup problem must
     * not stop a launch.
     */
    @Test
    void shouldNotThrow_whenLogDirectoryDoesNotExist() {
        // GIVEN
        Path missing = logDirectory.resolve( "missing" );

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerLogRetention.removeDaysBefore( missing, OLDEST_KEPT ) );

        // THEN
        assertThat( thrown ).isNull();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Creates the directory of a day holding one log file.
     *
     * @param day
     *         day the directory is named after
     * @return the created day directory
     * @throws IOException
     *         if it cannot be created
     */
    private Path givenDayDirectory( LocalDate day ) throws IOException {
        Path directory = Files.createDirectories( ServerLogDirectories.dayDirectory( logDirectory, day ) );
        Files.createFile( directory.resolve( "run.log" ) );
        return directory;
    }
}
