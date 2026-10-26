package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Identity checked before sending a signal to a Windows process, including from a separate JVM.
 * A PID alone cannot distinguish the intended process from a later process reusing that PID.
 *
 * @param pid
 *         positive process identifier representable as a Windows DWORD, excluding reserved values
 * @param startedAt
 *         process creation instant reported by the operating system, not the panel's clock
 * @param executable
 *         normalized absolute executable path; capture resolves it to its real path
 */
@Log4j2
public record ProcessIdentity( long pid, @Nonnull Instant startedAt, @Nonnull Path executable ) {

    /**
     * Validates the identity without performing I/O.
     *
     * @throws NullPointerException
     *         if either reference is null
     * @throws IllegalArgumentException
     *         if the PID is invalid or the executable path is not absolute and normalized
     */
    public ProcessIdentity {
        requireNonNull( startedAt, "startedAt must not be null" );
        requireNonNull( executable, "executable must not be null" );
        if ( pid <= 0 || pid >= 0xffff_ffffL ) {
            throw new IllegalArgumentException( "pid must be a positive, non-reserved Windows DWORD" );
        }
        if ( !executable.isAbsolute() || !executable.equals( executable.normalize() ) ) {
            throw new IllegalArgumentException( "executable must be absolute and normalized" );
        }
    }

    /**
     * Captures the operating-system identity of a process and checks its executable.
     *
     * @param handle
     *         process obtained when launching the server
     * @param expectedExecutable
     *         executable the launcher was asked to create
     * @return verified identity with a real executable path
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalStateException
     *         if the process is dead or its identity cannot be confirmed
     * @throws UncheckedIOException
     *         if an executable path cannot be resolved
     */
    public static @Nonnull ProcessIdentity capture( @Nonnull ProcessHandle handle,
                                                   @Nonnull Path expectedExecutable ) {
        requireNonNull( handle, "handle must not be null" );
        requireNonNull( expectedExecutable, "expectedExecutable must not be null" );
        ProcessHandle.Info information = handle.info();
        Instant createdAt = information.startInstant().orElseThrow(
                () -> new IllegalStateException( "Process creation time is unavailable" ) );
        Path command = realPath( Path.of( information.command().orElseThrow(
                () -> new IllegalStateException( "Process executable is unavailable" ) ) ) );
        if ( !handle.isAlive() || !command.equals( realPath( expectedExecutable ) ) ) {
            throw new IllegalStateException( "Process executable or liveness does not match the launch" );
        }
        ProcessIdentity identity = new ProcessIdentity( handle.pid(), createdAt, command );
        log.debug( "Verified executable and operating-system creation time for process {}", identity.pid() );
        return identity;
    }

    /**
     * Checks a process again immediately before a signal or termination.
     *
     * @param handle
     *         process whose identity is to be verified
     * @throws NullPointerException
     *         if the handle is null
     * @throws IllegalStateException
     *         if the PID, creation time, executable or liveness differs
     * @throws UncheckedIOException
     *         if the executable path cannot be resolved
     */
    public void verify( @Nonnull ProcessHandle handle ) {
        requireNonNull( handle, "handle must not be null" );
        if ( handle.pid() != pid || !equals( capture( handle, executable ) ) ) {
            throw new IllegalStateException( "Process identity no longer matches the managed server" );
        }
    }

    private static @Nonnull Path realPath( @Nonnull Path path ) {
        try {
            return path.toRealPath();
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Cannot verify the process executable", exception );
        }
    }
}
