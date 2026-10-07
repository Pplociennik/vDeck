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
 * <p>The handle belongs to a newly created or explicitly adopted process. ProcessIdentity verifies
 * its PID, operating-system creation time and actual executable before every signal or termination.
 * A successfully created server may outlive the panel; a PID alone never grants recovery ownership.
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
