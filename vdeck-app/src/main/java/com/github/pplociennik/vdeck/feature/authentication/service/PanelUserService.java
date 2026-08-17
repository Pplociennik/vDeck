package com.github.pplociennik.vdeck.feature.authentication.service;

import com.github.pplociennik.vdeck.feature.authentication.domain.PasswordChangeResult;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import jakarta.annotation.Nonnull;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Defines authentication-facing operations for loading panel users and replacing their passwords.
 */
public interface PanelUserService extends UserDetailsService {

    /**
     * Temporary authority limiting a user to the mandatory password-change flow.
     */
    @Nonnull
    String PASSWORD_CHANGE_REQUIRED_AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    /**
     * Replaces an authenticated user's password when all input and credential checks succeed.
     *
     * @param username
     *         authenticated login name whose password is being changed
     * @param passwordChangeRequest
     *         validated current password, requested replacement and confirmation
     * @return expected result of validation and password replacement
     */
    @Nonnull
    PasswordChangeResult changePassword(
            @Nonnull String username, @Nonnull PasswordChangeRequest passwordChangeRequest );
}
