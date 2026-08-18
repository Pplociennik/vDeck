package com.github.pplociennik.vdeck.feature.authentication.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the two secret values submitted while replacing the initial password of a panel account.
 * The screen using this record is reachable only for an account still holding its initial credentials,
 * which the security chain has already verified, so no current password is asked for.
 * Its textual representation is deliberately redacted because record defaults would expose every component.
 *
 * @param newPassword
 *         requested plaintext password containing between 12 and 128 characters
 * @param confirmation
 *         repeated requested plaintext password
 */
public record InitialPasswordChangeRequest(
        @Nullable @NotBlank @Size( min = 12, max = 128 ) String newPassword,
        @Nullable @NotBlank @Size( min = 12, max = 128 ) String confirmation ) {

    /**
     * Returns a representation that never includes submitted password values.
     *
     * @return fixed redacted description of this request
     */
    @Override
    public String toString() {
        return "InitialPasswordChangeRequest[newPassword=<redacted>, confirmation=<redacted>]";
    }
}