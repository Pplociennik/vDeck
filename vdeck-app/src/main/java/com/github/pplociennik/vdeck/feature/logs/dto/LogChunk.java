package com.github.pplociennik.vdeck.feature.logs.dto;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Part of the current launch's game server log returned to one console poll.
 *
 * @param source
 *         file name of the log the text comes from, or {@code null} when no launch has a log; the browser
 *         starts over when it changes. Only the name is exposed, never the installation path
 * @param offset
 *         byte position the next poll continues from
 * @param text
 *         complete lines read since the requested position; empty when nothing new was written
 * @param reset
 *         {@code true} when the text does not continue the requested position, so the browser discards
 *         what it shows before appending
 */
public record LogChunk( @Nullable String source, long offset, @Nonnull String text, boolean reset ) {

    /**
     * Validates the chunk.
     *
     * @throws NullPointerException
     *         if {@code text} is {@code null}
     */
    public LogChunk {
        requireNonNull( text, "text must not be null" );
    }

    /**
     * Returns the chunk of a panel that has no launch log to show.
     *
     * @return chunk without a source and without text, telling the browser to clear the console
     */
    public static @Nonnull LogChunk none() {
        return new LogChunk( null, 0, "", true );
    }
}
