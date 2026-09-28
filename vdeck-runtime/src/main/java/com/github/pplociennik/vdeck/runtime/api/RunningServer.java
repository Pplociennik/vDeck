package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * The Valheim process currently owned by the panel, together with the moment it was created and the
 * log file this particular launch writes to.
 *
 * <p>Every component is always present, so this is a plain carrier: an instance exists only while a
 * process is genuinely being managed.
 *
 * <p>The process is held as a live {@link ProcessHandle} obtained when it was created, never looked up
 * again by its identifier. That is what makes a bare process identifier safe here despite
 * {@code security.md} §4: the panel never has to decide whether some process it found is the one it
 * started, because it never lets go of the handle. A managed process also cannot outlive the panel —
 * see {@link ServerRuntime} — so a stale identifier has no chance to be reused behind its back.
 *
 * @param handle
 *         live handle of the managed process
 * @param startedAt
 *         moment the process was created
 * @param logFile
 *         absolute path the server writes its own log to during this launch
 */
public record RunningServer( @Nonnull ProcessHandle handle, @Nonnull Instant startedAt, @Nonnull Path logFile ) {

    /**
     * Creates the description of a managed process.
     *
     * @throws NullPointerException
     *         if any component is {@code null}
     * @throws IllegalArgumentException
     *         if the log file path is not absolute
     */
    public RunningServer {
        requireNonNull( handle, "handle must not be null" );
        requireNonNull( startedAt, "startedAt must not be null" );
        requireNonNull( logFile, "logFile must not be null" );
        if ( !logFile.isAbsolute() ) {
            throw new IllegalArgumentException( "logFile must be absolute but was " + logFile );
        }
    }
}