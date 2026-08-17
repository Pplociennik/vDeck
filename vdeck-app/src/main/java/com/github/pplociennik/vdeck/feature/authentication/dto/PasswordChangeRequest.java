package com.github.pplociennik.vdeck.feature.authentication.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the three secret values submitted during password replacement.
 * Its textual representation is deliberately redacted because record defaults would expose every component.
 *
 * @param currentPassword
 *         current plaintext password
 * @param newPassword
 *         requested plaintext password containing between 12 and 128 characters
 * @param confirmation
 *         repeated requested plaintext password
 */
public record PasswordChangeRequest(
        @Nullable @NotBlank @Size( max = 128 ) String currentPassword,
        @Nullable @NotBlank @Size( min = 12, max = 128 ) String newPassword,
        @Nullable @NotBlank @Size( min = 12, max = 128 ) String confirmation ) {

    /**
     * Returns a representation that never includes submitted password values.
     *
     * @return fixed redacted description of this request
     */
    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=<redacted>, newPassword=<redacted>, "
                + "confirmation=<redacted>]";
    }
}
