package com.github.pplociennik.vdeck.feature.authentication.service.impl;

import com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage;
import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.dto.InitialPasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Implements persistent panel-user loading and password replacement for the authentication feature.
 */
@Log4j2
@Service
class PanelUserServiceImpl implements PanelUserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service using persistent users and the shared password encoder.
     *
     * @param userRepository
     *         repository owning panel-user records
     * @param passwordEncoder
     *         encoder used for password verification and replacement
     */
    PanelUserServiceImpl( @Nonnull UserRepository userRepository,
                          @Nonnull PasswordEncoder passwordEncoder ) {
        this.userRepository = requireNonNull( userRepository, "userRepository must not be null" );
        this.passwordEncoder = requireNonNull( passwordEncoder, "passwordEncoder must not be null" );
    }

    /**
     * Loads one panel user for Spring Security and exposes its role and current account restrictions.
     *
     * @param username
     *         login name supplied to Spring Security
     * @return Spring Security user details backed by the persistent panel user
     * @throws UsernameNotFoundException
     *         if the supplied username has no persistent account
     */
    @Override
    @Transactional
    public @Nonnull UserDetails loadUserByUsername( @Nonnull String username ) throws UsernameNotFoundException {
        requireNonNull( username, "username must not be null" );
        PanelUser panelUser = userRepository.findById( username )
                .orElseThrow( () -> new UsernameNotFoundException( "Panel user was not found" ) );
        return toUserDetails( panelUser );
    }

    /**
     * Replaces an authenticated user's password when all input and credential checks succeed.
     * The checks run in a fixed order and the first failure ends the operation: current password
     * match, confirmation match, difference from the current password and the required mix of
     * character kinds. Password values are neither normalized nor logged.
     *
     * @param username
     *         authenticated login name whose password is being changed
     * @param passwordChangeRequest
     *         validated current password, requested replacement and confirmation
     * @throws PasswordChangeException
     *         if a check rejects the replacement; the stored password is unchanged
     * @throws UsernameNotFoundException
     *         if the authenticated account no longer exists
     */
    @Override
    @Transactional
    public void changePassword(
            @Nonnull String username, @Nonnull PasswordChangeRequest passwordChangeRequest ) {
        requireNonNull( username, "username must not be null" );
        requireNonNull( passwordChangeRequest, "passwordChangeRequest must not be null" );
        PanelUser panelUser = findAuthenticatedUser( username );

        String currentPassword = passwordChangeRequest.currentPassword();
        if ( currentPassword == null || !passwordEncoder.matches( currentPassword, panelUser.getPasswordHash() ) ) {
            throw new PasswordChangeException( PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT );
        }

        applyNewPassword( panelUser, passwordChangeRequest.newPassword(), passwordChangeRequest.confirmation() );
        log.info( "Panel user password was changed on the user's request" );
    }

    /**
     * Replaces the initial password of an account that still has to leave the first-login state.
     * Knowledge of the current password is not proven here; every remaining check of
     * {@link #changePassword} still runs in the same order and the first failure ends the operation.
     * Password values are neither normalized nor logged.
     *
     * @param username
     *         authenticated login name whose initial password is being replaced
     * @param initialPasswordChangeRequest
     *         validated requested replacement and its confirmation
     * @throws PasswordChangeException
     *         if a check rejects the replacement, never with
     *         {@link PasswordChangeMessage#CURRENT_PASSWORD_INCORRECT}; the stored password is unchanged
     * @throws UsernameNotFoundException
     *         if the authenticated account no longer exists
     * @throws IllegalStateException
     *         if the account no longer requires replacement of its initial password, because skipping
     *         the current-password check is only defensible while the account uses credentials it has
     *         never chosen
     */
    @Override
    @Transactional
    public void changeInitialPassword(
            @Nonnull String username, @Nonnull InitialPasswordChangeRequest initialPasswordChangeRequest ) {
        requireNonNull( username, "username must not be null" );
        requireNonNull( initialPasswordChangeRequest, "initialPasswordChangeRequest must not be null" );
        PanelUser panelUser = findAuthenticatedUser( username );
        if ( !panelUser.isPasswordChangeRequired() ) {
            throw new IllegalStateException( "Initial password replacement is not pending for this account" );
        }

        applyNewPassword(
                panelUser, initialPasswordChangeRequest.newPassword(), initialPasswordChangeRequest.confirmation() );
        log.info( "Initial panel user password was replaced and the first-login restriction was removed" );
    }

    /**
     * Validates a requested replacement and persists it through the managed entity when every check
     * passes. The caller is responsible for whatever proof of identity its own flow requires.
     *
     * @param panelUser
     *         managed account whose password hash is replaced on success
     * @param newPassword
     *         requested plaintext replacement, possibly {@code null} when the form was incomplete
     * @param confirmation
     *         repeated plaintext replacement, possibly {@code null} when the form was incomplete
     * @throws PasswordChangeException
     *         carrying the first failed check; nothing was persisted
     */
    private void applyNewPassword( @Nonnull PanelUser panelUser, @Nullable String newPassword,
                                   @Nullable String confirmation ) {
        if ( newPassword == null || !newPassword.equals( confirmation ) ) {
            throw new PasswordChangeException( PasswordChangeMessage.NEW_PASSWORD_MISMATCH );
        }
        if ( passwordEncoder.matches( newPassword, panelUser.getPasswordHash() ) ) {
            throw new PasswordChangeException( PasswordChangeMessage.NEW_PASSWORD_UNCHANGED );
        }
        if ( !containsRequiredCharacterKinds( newPassword ) ) {
            throw new PasswordChangeException( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK );
        }

        panelUser.changePassword( passwordEncoder.encode( newPassword ) );
    }

    /**
     * Checks that the password mixes all four required character kinds: a digit, a lower case letter,
     * an upper case letter and a special character. Every character that is neither a letter, a digit
     * nor whitespace counts as special, so a space alone does not satisfy the requirement.
     *
     * @param password
     *         plaintext replacement password
     * @return {@code true} when all four character kinds are present
     */
    private boolean containsRequiredCharacterKinds( @Nonnull String password ) {
        boolean hasDigit = false;
        boolean hasLowerCase = false;
        boolean hasUpperCase = false;
        boolean hasSpecial = false;
        for ( char character : password.toCharArray() ) {
            if ( Character.isDigit( character ) ) {
                hasDigit = true;
            } else if ( Character.isLowerCase( character ) ) {
                hasLowerCase = true;
            } else if ( Character.isUpperCase( character ) ) {
                hasUpperCase = true;
            } else if ( !Character.isWhitespace( character ) ) {
                hasSpecial = true;
            }
        }
        return hasDigit && hasLowerCase && hasUpperCase && hasSpecial;
    }

    private @Nonnull PanelUser findAuthenticatedUser( @Nonnull String username ) {
        return userRepository.findById( username )
                .orElseThrow( () -> new UsernameNotFoundException( "Authenticated panel user was not found" ) );
    }

    private @Nonnull UserDetails toUserDetails( @Nonnull PanelUser panelUser ) {
        List< GrantedAuthority > authorities = new ArrayList<>();
        authorities.add( new SimpleGrantedAuthority( "ROLE_" + panelUser.getRole().name() ) );
        if ( panelUser.isPasswordChangeRequired() ) {
            authorities.add( new SimpleGrantedAuthority( PASSWORD_CHANGE_REQUIRED_AUTHORITY ) );
        }
        return User.withUsername( panelUser.getUsername() )
                .password( panelUser.getPasswordHash() )
                .authorities( authorities )
                .build();
    }
}