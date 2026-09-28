package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Everything the runtime needs to create the Valheim server process, already resolved and validated by
 * the application layer. The runtime does not read settings, resolve paths or apply defaults: it
 * receives a complete, absolute description and launches exactly that.
 *
 * <p><strong>This object carries a secret.</strong> The server password is one of the
 * {@linkplain #arguments() arguments}, because {@code valheim_server.exe} accepts it only on its
 * command line. {@link #toString()} therefore never renders the arguments, and callers must not log the
 * argument list either ({@code security.md} §7).
 *
 * @param executable
 *         absolute path of {@code valheim_server.exe}
 * @param arguments
 *         command-line arguments without the executable itself; frozen on construction
 * @param environment
 *         complete environment of the child process; frozen on construction
 * @param workingDirectory
 *         absolute working directory of the child process, which is the installation directory
 * @param logFile
 *         absolute path the server writes its own log to, passed to it through {@code -logFile}
 */
public record LaunchSpec(
        @Nonnull Path executable,
        @Nonnull List< String > arguments,
        @Nonnull Map< String, String > environment,
        @Nonnull Path workingDirectory,
        @Nonnull Path logFile ) {

    /**
     * Creates a launch description and freezes its collections.
     *
     * @throws NullPointerException
     *         if any component, or any element of {@code arguments} or {@code environment}, is
     *         {@code null}
     * @throws IllegalArgumentException
     *         if any of the three paths is not absolute
     */
    public LaunchSpec {
        requireNonNull( executable, "executable must not be null" );
        requireNonNull( workingDirectory, "workingDirectory must not be null" );
        requireNonNull( logFile, "logFile must not be null" );
        arguments = List.copyOf( requireNonNull( arguments, "arguments must not be null" ) );
        environment = Map.copyOf( requireNonNull( environment, "environment must not be null" ) );

        requireAbsolute( executable, "executable" );
        requireAbsolute( workingDirectory, "workingDirectory" );
        requireAbsolute( logFile, "logFile" );
    }

    /**
     * Returns a description safe to put in a log entry: it names the executable and the log file but
     * never the arguments, one of which may be the server password.
     *
     * @return redacted description of this launch
     */
    @Override
    public @Nonnull String toString() {
        return "LaunchSpec[executable=%s, arguments=<redacted, %d entries>, workingDirectory=%s, logFile=%s]"
                .formatted( executable, arguments.size(), workingDirectory, logFile );
    }

    private static void requireAbsolute( @Nonnull Path path, @Nonnull String name ) {
        if ( !path.isAbsolute() ) {
            throw new IllegalArgumentException( "%s must be absolute but was %s".formatted( name, path ) );
        }
    }
}