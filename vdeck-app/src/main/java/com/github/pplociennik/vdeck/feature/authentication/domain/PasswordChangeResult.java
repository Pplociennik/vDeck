package com.github.pplociennik.vdeck.feature.authentication.domain;

/**
 * Outcomes expected from a panel password-change request.
 */
public enum PasswordChangeResult {

    SUCCESS,
    CURRENT_PASSWORD_INCORRECT,
    NEW_PASSWORD_MISMATCH,
    NEW_PASSWORD_UNCHANGED
}
