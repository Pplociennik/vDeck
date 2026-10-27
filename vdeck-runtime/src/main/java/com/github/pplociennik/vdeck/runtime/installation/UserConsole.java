package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;

/**
 * Polish messages shown in the console window of the launcher and updater. Callers pass a message key,
 * whose text {@link I18n} resolves from the console translation bundle. The texts go through the
 * {@code vdeck.console} Log4j2 logger, which the launcher and updater configurations route to the
 * console, so the run log file records them too. Technical details stay in English class loggers.
 */
@Log4j2
public final class UserConsole {

    /**
     * Logger the launcher and updater configurations route to the console and the run log.
     */
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
     *         message key
     * @param arguments
     *         values of the message's placeholders, without secrets
     * @throws NullPointerException
     *         if the message or the argument array is null
     */
    public void show( @Nonnull ConsoleMessageIf message, @Nonnull Object... arguments ) {
        CONSOLE.info( I18n.text( message, arguments ) );
    }

    /**
     * Shows an error and keeps the console window open until Enter is pressed, so a window opened by
     * double-clicking the start script does not vanish with the explanation.
     *
     * @param message
     *         message key
     * @param arguments
     *         values of the message's placeholders, without secrets
     * @throws NullPointerException
     *         if the message or the argument array is null
     */
    public void showErrorAndWait( @Nonnull ConsoleMessageIf message, @Nonnull Object... arguments ) {
        CONSOLE.error( I18n.text( message, arguments ) );
        CONSOLE.error( I18n.text( ConsoleMessage.PRESS_ENTER ) );

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
