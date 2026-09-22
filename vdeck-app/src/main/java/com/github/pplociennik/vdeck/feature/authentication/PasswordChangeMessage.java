package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of messages a password replacement screen can show to the user.
 * Every constant carries only the translation key, so the wording stays in the translation files and
 * no component builds a user-facing text itself. A rejected password change carries the matching
 * constant inside its {@code PasswordChangeException}, so both password-change screens describe the same
 * outcome identically.
 */
public enum PasswordChangeMessage implements TranslationKeyIf {

    /**
     * Submitted fields did not pass annotation validation, including the required password length.
     */
    INVALID_INPUT( "changePassword.error.invalidInput" ),

    /**
     * The submitted current password does not match the stored one.
     */
    CURRENT_PASSWORD_INCORRECT( "changePassword.error.currentPasswordIncorrect" ),

    /**
     * The new password and its confirmation differ.
     */
    NEW_PASSWORD_MISMATCH( "changePassword.error.newPasswordMismatch" ),

    /**
     * The new password is identical to the current one.
     */
    NEW_PASSWORD_UNCHANGED( "changePassword.error.newPasswordUnchanged" ),

    /**
     * The new password does not contain all required character kinds.
     */
    NEW_PASSWORD_TOO_WEAK( "changePassword.error.newPasswordTooWeak" );

    private final String key;

    PasswordChangeMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Returns the translation key of this message.
     *
     * @return key resolved against the translation files
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
