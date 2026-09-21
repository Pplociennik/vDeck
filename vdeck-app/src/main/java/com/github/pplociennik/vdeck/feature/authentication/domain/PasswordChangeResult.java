package com.github.pplociennik.vdeck.feature.authentication.domain;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of a panel password-change request: what happened and, when the change was rejected, which
 * message the screen has to show. Carrying the message key here means no consumer has to translate a
 * status into wording of its own, so every screen describes the same outcome identically.
 *
 * @param status
 *         outcome kind of the request
 * @param messageKey
 *         translation key of the message explaining a rejection, {@code null} exactly when the change
 *         succeeded and there is nothing to explain
 */
public record PasswordChangeResult( @Nonnull PasswordChangeStatus status, @Nullable TranslationKeyIf messageKey ) {

    /**
     * Creates a result and enforces that a message key is present exactly for a rejected change.
     *
     * @throws NullPointerException
     *         if {@code status} is {@code null}
     * @throws IllegalArgumentException
     *         if a successful change carries a message key or a rejected one does not
     */
    public PasswordChangeResult {
        requireNonNull( status, "status must not be null" );
        if ( status == PasswordChangeStatus.SUCCESS && messageKey != null ) {
            throw new IllegalArgumentException( "A successful password change has no message key" );
        }
        if ( status != PasswordChangeStatus.SUCCESS && messageKey == null ) {
            throw new IllegalArgumentException( "A rejected password change requires a message key" );
        }
    }

    /**
     * Creates the result of a password change that passed every check.
     *
     * @return successful result without a message key
     */
    public static @Nonnull PasswordChangeResult success() {
        return new PasswordChangeResult( PasswordChangeStatus.SUCCESS, null );
    }

    /**
     * Creates the result of a rejected password change.
     *
     * @param status
     *         check that failed
     * @param messageKey
     *         translation key of the message explaining the rejection to the user
     * @return rejected result carrying its own message key
     * @throws IllegalArgumentException
     *         if {@link PasswordChangeStatus#SUCCESS} is passed, because a success has nothing to explain
     */
    public static @Nonnull PasswordChangeResult rejected( @Nonnull PasswordChangeStatus status,
                                                          @Nonnull TranslationKeyIf messageKey ) {
        requireNonNull( messageKey, "messageKey must not be null" );
        return new PasswordChangeResult( status, messageKey );
    }

    /**
     * Reports whether the password was actually replaced.
     *
     * @return {@code true} when the change passed every check
     */
    public boolean isSuccessful() {
        return status == PasswordChangeStatus.SUCCESS;
    }
}