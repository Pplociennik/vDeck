package com.github.pplociennik.vdeck.feature.players.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PlayerListFile} reads list files within their size limit and writes them whole.
 */
class PlayerListFileTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path file;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Places the list file in a save directory that does not exist yet.
     */
    @BeforeEach
    void setUp() {
        file = directory.resolve( "valheim-data" ).resolve( "bannedlist.txt" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a missing file reads as an empty, extendable list.
     *
     * @throws IOException
     *         if the read fails unexpectedly
     */
    @Test
    void shouldReturnEmptyMutableList_whenFileDoesNotExist() throws IOException {
        // GIVEN
        // no file and no save directory

        // WHEN
        List< String > result = PlayerListFile.readLines( file );

        // THEN
        assertThat( result ).isEmpty();
        assertThat( result.add( "Steam_76561198012345678" ) ).isTrue();
    }

    /**
     * Verifies that lines with Windows line endings are read without the line breaks and without a
     * leading byte order mark.
     *
     * @throws IOException
     *         if the file cannot be prepared or read
     */
    @Test
    void shouldReadLinesWithoutBreaksAndByteOrderMark_whenFileUsesWindowsEndings() throws IOException {
        // GIVEN
        Files.createDirectories( file.getParent() );
        Files.writeString( file, "﻿// comment\r\nSteam_76561198012345678\r\n", StandardCharsets.UTF_8 );

        // WHEN
        List< String > result = PlayerListFile.readLines( file );

        // THEN
        assertThat( result ).containsExactly( "// comment", "Steam_76561198012345678" );
    }

    /**
     * Verifies that writing creates the save directory and that the lines are read back unchanged.
     *
     * @throws IOException
     *         if the file cannot be written or read
     */
    @Test
    void shouldCreateDirectoryAndRoundTripLines_whenLinesAreWritten() throws IOException {
        // GIVEN
        List< String > lines = List.of( "// List banned players ID  ONE per line", "Xbox_2535412345678901" );

        // WHEN
        PlayerListFile.writeLines( file, lines );

        // THEN
        assertThat( PlayerListFile.readLines( file ) ).isEqualTo( lines );
        try ( var files = Files.list( file.getParent() ) ) {
            assertThat( files ).containsExactly( file );
        }
    }

    /**
     * Verifies that a file larger than the limit is refused instead of read.
     *
     * @throws IOException
     *         if the file cannot be prepared
     */
    @Test
    void shouldThrowException_whenFileExceedsLimit() throws IOException {
        // GIVEN
        Files.createDirectories( file.getParent() );
        Files.write( file, new byte[ PlayerListFile.MAX_FILE_BYTES + 1 ] );

        // WHEN
        Throwable thrown = catchThrowable( () -> PlayerListFile.readLines( file ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IOException.class ).hasMessageContaining( "size limit" );
    }

    /**
     * Verifies that a directory standing where the file should be is refused.
     *
     * @throws IOException
     *         if the directory cannot be prepared
     */
    @Test
    void shouldThrowException_whenPathIsDirectory() throws IOException {
        // GIVEN
        Files.createDirectories( file );

        // WHEN
        Throwable thrown = catchThrowable( () -> PlayerListFile.readLines( file ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IOException.class ).hasMessageContaining( "Not a regular file" );
    }

    /**
     * Verifies that missing arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        List< String > lines = List.of();

        // WHEN
        Throwable readFile = catchThrowable( () -> PlayerListFile.readLines( null ) );
        Throwable writeFile = catchThrowable( () -> PlayerListFile.writeLines( null, lines ) );
        Throwable writeLines = catchThrowable( () -> PlayerListFile.writeLines( file, null ) );

        // THEN
        assertThat( readFile ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "file" );
        assertThat( writeFile ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "file" );
        assertThat( writeLines ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "lines" );
    }
}
