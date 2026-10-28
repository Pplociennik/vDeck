package com.github.pplociennik.vdeck.feature.launch.dto;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;

/**
 * Carries the fields submitted from the "Dostęp" panel: a new server password or the request to remove
 * the stored one. The annotation validates the format of the password; the rule that it may not be a part
 * of the stored server name is checked by the launch profile service.
 *
 * <p>An empty {@code password} keeps the stored password, and {@code clearPassword} removes it. The
 * password is a secret, so it is never copied back into a rendered form and the textual representation
 * of this record redacts it. An unchecked checkbox is not submitted at all, so {@code clearPassword}
 * treats {@code null} as {@code false}.
 *
 * @param password
 *         new server password of 5 to 64 characters without surrounding whitespace or a double quote, or
 *         empty to keep the stored one
 * @param clearPassword
 *         whether the stored password is removed
 */
public record ServerAccessForm(
        @Nullable @Pattern( regexp = "|[^\\p{Cntrl}\\s\"][^\\p{Cntrl}\"]{3,62}[^\\p{Cntrl}\\s\"]" ) String password,
        @Nullable Boolean clearPassword ) implements LaunchProfileForm {

    /**
     * Creates the empty form shown for a stored profile. The password field stays empty, because the
     * stored password is never sent to the browser.
     *
     * @return form without a password and without the removal request
     */
    public static @Nonnull ServerAccessForm empty() {
        return new ServerAccessForm( null, false );
    }

    /**
     * Returns a copy of this form without the submitted password, used when the form is shown again after
     * a rejected update so the secret is never written back into the page.
     *
     * @return the same form with an empty password field
     */
    @Override
    public @Nonnull ServerAccessForm withoutSecrets() {
        return new ServerAccessForm( null, clearPassword );
    }

    /**
     * Returns a representation that never includes the submitted password.
     *
     * @return description of this form with the password redacted
     */
    @Override
    public @Nonnull String toString() {
        return "ServerAccessForm[password=<redacted>, clearPassword=%s]".formatted( clearPassword );
    }
}
