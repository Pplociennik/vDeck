package com.github.pplociennik.vdeck.feature.players.service.impl;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Reads and writes the lines of a Valheim player list file: plain UTF-8 text with one entry or comment
 * per line.
 *
 * <p>Reading is bounded by {@link #MAX_FILE_BYTES} and refuses links, so an unexpected file never fills
 * memory or leads outside the save directory. Writing replaces the file atomically through
 * {@link DurableFiles#write}, so the server never reads a half-written list.
 */
final class PlayerListFile {

    /**
     * Largest list file read, in bytes: 1 MiB, tens of thousands of entries. A larger file is refused
     * rather than read, because no real list comes near it.
     */
    static final int MAX_FILE_BYTES = 1024 * 1024;

    /**
     * Byte order mark some editors put at the start of a UTF-8 file; it is not part of the first entry.
     */
    private static final String BYTE_ORDER_MARK = "﻿";

    private PlayerListFile() {
    }

    /**
     * Reads every line of a list file.
     *
     * @param file
     *         absolute path of the list file
     * @return mutable list of the file's lines without line breaks, empty when the file does not exist
     * @throws NullPointerException
     *         if the path is null
     * @throws IOException
     *         if the path is not a regular file, exceeds {@link #MAX_FILE_BYTES} or cannot be read
     */
    static @Nonnull List< String > readLines( @Nonnull Path file ) throws IOException {
        requireNonNull( file, "file must not be null" );
        if ( Files.notExists( file, NOFOLLOW_LINKS ) ) {
            return new ArrayList<>();
        }

        String text = new String( DurableFiles.read( file, MAX_FILE_BYTES ), StandardCharsets.UTF_8 );
        if ( text.startsWith( BYTE_ORDER_MARK ) ) {
            text = text.substring( BYTE_ORDER_MARK.length() );
        }
        return new ArrayList<>( text.lines().toList() );
    }

    /**
     * Replaces a list file with the given lines, each ended by the system line separator, creating the
     * directory when it does not exist yet.
     *
     * @param file
     *         absolute path of the list file
     * @param lines
     *         complete new lines of the file
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if the directory cannot be created or the file cannot be written and renamed atomically
     */
    static void writeLines( @Nonnull Path file, @Nonnull List< String > lines ) throws IOException {
        requireNonNull( file, "file must not be null" );
        requireNonNull( lines, "lines must not be null" );

        StringBuilder text = new StringBuilder();
        for ( String line : lines ) {
            text.append( line ).append( System.lineSeparator() );
        }
        Files.createDirectories( file.getParent() );
        DurableFiles.write( file, text.toString().getBytes( StandardCharsets.UTF_8 ) );
    }
}
