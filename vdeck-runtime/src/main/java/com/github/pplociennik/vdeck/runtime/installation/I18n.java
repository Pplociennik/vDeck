package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import static java.util.Objects.requireNonNull;

/**
 * Resolves console texts of the launcher and updater from the {@value #BUNDLE} bundle on the classpath,
 * the console counterpart of the panel's {@code messages.properties} (AD-009).
 *
 * <p>Arguments are formatted by {@link MessageFormat} with the Polish locale, so a pattern formats a number
 * explicitly ({@code {0,number,#}} for an integer, {@code {0,number,0.0}} for one decimal) and doubles a
 * literal apostrophe. A missing key is returned as the text, like the panel's
 * {@code use-code-as-default-message}, so a missing translation never stops a start.
 */
@Log4j2
public final class I18n {

    /**
     * Base name of the console translation bundle.
     */
    public static final String BUNDLE = "console-messages";

    /**
     * Locale of every console text and of the numbers formatted into it.
     */
    private static final Locale POLISH = Locale.forLanguageTag( "pl" );

    /**
     * Console translation bundle, loaded once.
     */
    private static final ResourceBundle MESSAGES = ResourceBundle.getBundle( BUNDLE, Locale.ROOT );

    private I18n() {
    }

    /**
     * Returns the Polish text of a message with its arguments filled in.
     *
     * @param message
     *         message key
     * @param arguments
     *         values of the pattern's placeholders, in order
     * @return formatted text, or the key itself when the bundle lacks it
     * @throws NullPointerException
     *         if the message or the argument array is null
     */
    public static @Nonnull String text( @Nonnull ConsoleMessageIf message, @Nonnull Object... arguments ) {
        requireNonNull( message, "message must not be null" );
        requireNonNull( arguments, "arguments must not be null" );

        if ( !MESSAGES.containsKey( message.getKey() ) ) {
            log.warn( "Missing console text {}", message.getKey() );
            return message.getKey();
        }
        return new MessageFormat( MESSAGES.getString( message.getKey() ), POLISH ).format( arguments );
    }
}
