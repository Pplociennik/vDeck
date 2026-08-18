package com.github.pplociennik.vdeck.feature.authentication.domain;

/**
 * Outcome kinds expected from a panel password-change request.
 * The status says what happened; the wording shown to the user travels next to it in
 * {@link PasswordChangeResult}.
 */
public enum PasswordChangeStatus {

    /**
     * Every check passed and the new password has been persisted.
     */
    SUCCESS,

    /**
     * The submitted current password does not match the stored one.
     */
    CURRENT_PASSWORD_INCORRECT,

    /**
     * The new password and its confirmation differ.
     */
    NEW_PASSWORD_MISMATCH,

    /**
     * The new password is identical to the current one.
     */
    NEW_PASSWORD_UNCHANGED,

    /**
     * The new password does not contain all required character kinds.
     */
    NEW_PASSWORD_TOO_WEAK
}