package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

/**
 * Polish messages shown in the console window of the launcher and updater. They go through the
 * {@code vdeck.console} Log4j2 logger, which the launcher and updater configurations route to the
 * console, so the run log file records them too. Technical details stay in English class loggers.
 */
@Log4j2
public final class UserConsole {

    private static final Logger CONSOLE = LogManager.getLogger( "vdeck.console" );

    private final InputStream input;

    /**
     * Creates a console reading confirmations from the standard input.
     */
    public UserConsole() {
        this( System.in );
    }

    /**
     * Creates a console reading confirmations from the given stream.
     *
     * @param input
     *         stream providing the Enter key press
     */
    UserConsole( @Nonnull InputStream input ) {
        this.input = requireNonNull( input, "input must not be null" );
    }

    /**
     * Shows an informational message.
     *
     * @param message
     *         Polish text without secrets
     * @throws NullPointerException
     *         if the message is null
     */
    public void show( @Nonnull String message ) {
        CONSOLE.info( requireNonNull( message, "message must not be null" ) );
    }

    /**
     * Shows an error and keeps the console window open until Enter is pressed, so a window opened by
     * double-clicking the start script does not vanish with the explanation.
     *
     * @param message
     *         Polish text without secrets
     * @throws NullPointerException
     *         if the message is null
     */
    public void showErrorAndWait( @Nonnull String message ) {
        CONSOLE.error( requireNonNull( message, "message must not be null" ) );
        CONSOLE.error( "Naciśnij Enter, aby zamknąć okno." );
        try {
            int read = input.read();
            while ( read >= 0 && read != '\n' ) {
                read = input.read();
            }
        } catch ( IOException exception ) {
            log.debug( "Could not wait for the Enter key", exception );
        }
    }
}
