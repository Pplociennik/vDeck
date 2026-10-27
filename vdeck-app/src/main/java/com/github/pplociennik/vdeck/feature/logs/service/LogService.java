package com.github.pplociennik.vdeck.feature.logs.service;

import com.github.pplociennik.vdeck.feature.logs.dto.LogChunk;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Reads the game server log of the current launch for the console screen, one poll at a time.
 *
 * <p>The log file is always the one the runtime reports for the current launch; a caller can only choose
 * where to continue, never which file is read.
 */
public interface LogService {

    /**
     * Returns the complete lines written to the current launch's log since the given position.
     *
     * <p>Without a position, or with one the file no longer reaches, the chunk restarts from the end of
     * the log and is marked as a reset. A launch whose log file does not exist yet yields an empty chunk.
     *
     * @param offset
     *         byte position returned by the previous poll, or {@code null} for the first poll
     * @return the new part of the log, or an empty chunk without a source when no launch has a log
     * @throws java.io.UncheckedIOException
     *         if the existing log file cannot be read
     */
    @Nonnull
    LogChunk readCurrent( @Nullable Long offset );
}
