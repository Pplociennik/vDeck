package com.github.pplociennik.vdeck.feature.authentication.exc;

import com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Signals that a password replacement was rejected by one of its checks, which ends the whole change;
 * the stored password is unchanged. It is thrown by both password-change screens and answered by the
 * authentication feature's controller advice, which sends the user back to the screen with the message.
 *
 * <p>The exception carries the message shown to the user as a {@link PasswordChangeMessage}, never as a
 * text, and never holds a submitted password.
 */
public class PasswordChangeException extends RuntimeException {

    private final PasswordChangeMessage messageKey;

    /**
     * Creates the exception for a rejected password replacement.
     *
     * @param messageKey
     *         message explaining the rejection to the user
     */
    public PasswordChangeException( @Nonnull PasswordChangeMessage messageKey ) {
        super( "Password change was rejected: " + requireNonNull( messageKey, "messageKey must not be null" ).name() );
        this.messageKey = messageKey;
    }

    /**
     * Returns the message the screen has to show.
     *
     * @return translation key of the message explaining the rejection
     */
    public @Nonnull PasswordChangeMessage getMessageKey() {
        return messageKey;
    }
}
