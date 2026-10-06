package com.github.pplociennik.vdeck.feature.server.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the directory actions of {@link ServerLogDirectories} on an isolated temporary directory.
 */
class ServerLogDirectoriesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDate DAY = LocalDate.of( 2026, 10, 6 );

    @TempDir
    private Path logDirectory;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the directory of a day is named after the day in the {@code yyyy-MM-dd} form, below
     * the log directory.
     */
    @Test
    void shouldNameDirectoryAfterDay_whenDayDirectoryIsResolved() {
        // GIVEN
        LocalDate day = LocalDate.of( 2026, 1, 5 );

        // WHEN
        Path result = ServerLogDirectories.dayDirectory( logDirectory, day );

        // THEN
        assertThat( result ).isEqualTo( logDirectory.resolve( "2026-01-05" ) );
    }

    /**
     * Verifies that the day is read back from a directory named after it.
     */
    @Test
    void shouldReturnDay_whenDirectoryIsNamedAfterDate() {
        // GIVEN
        Path directory = ServerLogDirectories.dayDirectory( logDirectory, DAY );

        // WHEN
        var result = ServerLogDirectories.dayOf( directory );

        // THEN
        assertThat( result ).contains( DAY );
    }

    /**
     * Verifies that a directory whose name is not a date in the {@code yyyy-MM-dd} form is not taken for
     * a day directory.
     *
     * @param name
     *         directory name that is not a day
     */
    @ParameterizedTest( name = "[{index}] name={0} is not a day" )
    @ValueSource( strings = { "archive", "2026-13-01", "06-10-2026", "2026-10-06_12-00-00" } )
    void shouldReturnEmpty_whenDirectoryIsNotNamedAfterDate( String name ) {
        // GIVEN
        Path directory = logDirectory.resolve( name );

        // WHEN
        var result = ServerLogDirectories.dayOf( directory );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a missing directory is created together with its missing parents.
     */
    @Test
    void shouldCreateDirectory_whenItDoesNotExist() {
        // GIVEN
        Path directory = logDirectory.resolve( "nested" ).resolve( "2026-10-06" );

        // WHEN
        ServerLogDirectories.createIfMissing( directory );

        // THEN
        assertThat( directory ).isDirectory();
    }

    /**
     * Verifies that an existing directory is left as it is, together with the files inside it.
     *
     * @throws IOException
     *         if the existing directory cannot be prepared
     */
    @Test
    void shouldKeepDirectoryContents_whenDirectoryAlreadyExists() throws IOException {
        // GIVEN
        Path directory = Files.createDirectories( logDirectory.resolve( "2026-10-06" ) );
        Path earlierLog = Files.writeString( directory.resolve( "earlier.log" ), "earlier launch" );

        // WHEN
        ServerLogDirectories.createIfMissing( directory );

        // THEN
        assertThat( directory ).isDirectory();
        assertThat( earlierLog ).hasContent( "earlier launch" );
    }

    /**
     * Verifies that a path occupied by a file is reported, because no directory can be created there.
     *
     * @throws IOException
     *         if the blocking file cannot be created
     */
    @Test
    void shouldThrowException_whenFileOccupiesDirectoryPath() throws IOException {
        // GIVEN
        Path blocked = Files.createFile( logDirectory.resolve( "blocked" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerLogDirectories.createIfMissing( blocked ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to create game log directory" );
    }

    /**
     * Verifies that a directory is removed together with its nested directories and files.
     *
     * @throws IOException
     *         if the directory tree cannot be prepared
     */
    @Test
    void shouldRemoveWholeTree_whenDirectoryIsDeleted() throws IOException {
        // GIVEN
        Path directory = Files.createDirectories( logDirectory.resolve( "2026-09-01" ) );
        Files.createFile( directory.resolve( "run.log" ) );
        Files.createFile( Files.createDirectories( directory.resolve( "nested" ) ).resolve( "inner.log" ) );

        // WHEN
        ServerLogDirectories.deleteRecursively( directory );

        // THEN
        assertThat( directory ).doesNotExist();
        assertThat( logDirectory ).isDirectory();
    }

    /**
     * Verifies that removing a directory that does not exist is a logged no-op rather than an error.
     */
    @Test
    void shouldNotThrow_whenDeletedDirectoryDoesNotExist() {
        // GIVEN
        Path missing = logDirectory.resolve( "missing" );

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerLogDirectories.deleteRecursively( missing ) );

        // THEN
        assertThat( thrown ).isNull();
        assertThat( logDirectory ).isDirectory();
    }
}
