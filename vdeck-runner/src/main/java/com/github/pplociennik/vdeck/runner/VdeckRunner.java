package com.github.pplociennik.vdeck.runner;

import com.github.pplociennik.vdeck.runtime.api.ControlSenderCommand;
import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.windows.WindowsConsoleControlSender;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/** Executes the isolated console helper without loading the panel or starting Spring. */
@Log4j2
public final class VdeckRunner {

    private final Consumer< ProcessIdentity > controlSender;

    /**
     * Creates the helper with a native signaling boundary invoked only after argument validation.
     *
     * @param controlSender
     *         identity verification and native signal operation
     * @throws NullPointerException
     *         if controlSender is null
     */
    VdeckRunner( @Nonnull Consumer< ProcessIdentity > controlSender ) {
        this.controlSender = requireNonNull( controlSender, "controlSender must not be null" );
    }

    /**
     * Executes one helper request and exits with 0 for sending, 1 for failure or 2 for invalid arguments.
     * No native sender is created until the command has been parsed successfully.
     *
     * @param arguments
     *         {@code --ctrl-sender <pid> <creation-instant> <absolute-executable>}
     * @throws NullPointerException
     *         if arguments is null
     */
    public static void main( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        System.exit( new VdeckRunner( identity -> new WindowsConsoleControlSender().send( identity ) )
                .run( arguments ) );
    }

    /**
     * Validates a request, sends the signal and translates failures to helper exit codes.
     * Successful sending does not prove that the server saved its world or exited.
     *
     * @param arguments
     *         exactly one technical helper command
     * @return 0 for sending, 1 for signal or identity failure, 2 for invalid command input
     * @throws NullPointerException
     *         if the argument array is null
     */
    int run( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        ProcessIdentity identity;
        try {
            identity = ControlSenderCommand.parse( arguments );
        } catch ( RuntimeException exception ) {
            log.error( "Invalid console sender command" );
            return 2;
        }
        try {
            log.debug( "Sending console event to process {}", identity.pid() );
            controlSender.accept( identity );
            log.info( "Console event sent to process {}", identity.pid() );
            return 0;
        } catch ( RuntimeException exception ) {
            log.error( "Console sender failed for process {}", identity.pid(), exception );
            return 1;
        }
    }
}
