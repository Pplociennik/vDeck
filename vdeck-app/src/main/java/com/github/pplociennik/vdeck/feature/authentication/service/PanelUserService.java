package com.github.pplociennik.vdeck.feature.authentication.service;

import com.github.pplociennik.vdeck.feature.authentication.dto.InitialPasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import jakarta.annotation.Nonnull;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Defines authentication-facing operations for loading panel users and replacing their passwords.
 * A password is replaced through one of two separate operations: {@link #changePassword} for a change
 * requested by a user who already works with the panel, and {@link #changeInitialPassword} for the
 * mandatory replacement of credentials that have never been used.
 */
public interface PanelUserService extends UserDetailsService {

    /**
     * Temporary authority limiting a user to the mandatory password-change flow.
     */
    @Nonnull
    String PASSWORD_CHANGE_REQUIRED_AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    /**
     * Replaces an authenticated user's password when all input and credential checks succeed.
     * The checks are applied in a fixed order: the current password has to match, the replacement has
     * to match its confirmation, differ from the current password and contain a digit, a lower case
     * letter, an upper case letter and a special character.
     *
     * @param username
     *         authenticated login name whose password is being changed
     * @param passwordChangeRequest
     *         validated current password, requested replacement and confirmation
     * @throws PasswordChangeException
     *         if a check rejects the replacement; the stored password is unchanged
     */
    void changePassword(
            @Nonnull String username, @Nonnull PasswordChangeRequest passwordChangeRequest );

    /**
     * Replaces the initial password of an account that still has to leave the first-login state.
     * Knowledge of the current password is not proven here: the account is known to use credentials it
     * has never chosen, so requiring them would only make the user retype the published default. Every
     * remaining check of {@link #changePassword} still applies, in the same order: the replacement has
     * to match its confirmation, differ from the current password and contain a digit, a lower case
     * letter, an upper case letter and a special character.
     *
     * @param username
     *         authenticated login name whose initial password is being replaced
     * @param initialPasswordChangeRequest
     *         validated requested replacement and its confirmation
     * @throws PasswordChangeException
     *         if a check rejects the replacement, never with
     *         {@link com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage#CURRENT_PASSWORD_INCORRECT};
     *         the stored password is unchanged
     * @throws IllegalStateException
     *         if the account no longer requires replacement of its initial password
     */
    void changeInitialPassword(
            @Nonnull String username, @Nonnull InitialPasswordChangeRequest initialPasswordChangeRequest );
}