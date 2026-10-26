package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** Parses the technical helper mode without loading Spring or calling native Windows functions. */
public final class ControlSenderCommand {

    /** Argument reserving execution for the isolated console-signal helper. */
    public static final String MODE = "--ctrl-sender";

    private ControlSenderCommand() {
    }

    /**
     * Parses {@code --ctrl-sender <pid> <creation-instant> <absolute-executable>}.
     *
     * @param arguments
     *         exactly four arguments; no arguments are interpreted by a shell
     * @return the expected identity to recheck inside the helper
     * @throws NullPointerException
     *         if the argument array or an argument is null
     * @throws IllegalArgumentException
     *         if the command or identity is malformed
     */
    public static @Nonnull ProcessIdentity parse( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        for ( String argument : arguments ) {
            requireNonNull( argument, "argument must not be null" );
        }
        if ( arguments.length != 4 || !MODE.equals( arguments[0] ) ) {
            throw new IllegalArgumentException( "Invalid console sender command" );
        }
        try {
            return new ProcessIdentity( Long.parseLong( arguments[1] ), Instant.parse( arguments[2] ),
                    Path.of( arguments[3] ) );
        } catch ( RuntimeException exception ) {
            throw new IllegalArgumentException( "Invalid console sender identity", exception );
        }
    }

    /**
     * Renders separate helper arguments from an already captured identity.
     *
     * @param identity
     *         managed process identity, without server arguments or secrets
     * @return immutable helper argument list
     * @throws NullPointerException
     *         if the identity is null
     */
    public static @Nonnull List< String > arguments( @Nonnull ProcessIdentity identity ) {
        requireNonNull( identity, "identity must not be null" );
        return List.of( MODE, Long.toString( identity.pid() ), identity.startedAt().toString(),
                identity.executable().toString() );
    }
}
