package com.github.pplociennik.vdeck.feature.authentication.service.impl;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.domain.PasswordChangeResult;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import jakarta.annotation.Nonnull;
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
     * Password values are neither normalized nor logged.
     *
     * @param username
     *         authenticated login name whose password is being changed
     * @param passwordChangeRequest
     *         validated current password, requested replacement and confirmation
     * @return expected result of validation and password replacement
     * @throws UsernameNotFoundException
     *         if the authenticated account no longer exists
     */
    @Override
    @Transactional
    public @Nonnull PasswordChangeResult changePassword(
            @Nonnull String username, @Nonnull PasswordChangeRequest passwordChangeRequest ) {
        requireNonNull( username, "username must not be null" );
        requireNonNull( passwordChangeRequest, "passwordChangeRequest must not be null" );
        PanelUser panelUser = userRepository.findById( username )
                .orElseThrow( () -> new UsernameNotFoundException( "Authenticated panel user was not found" ) );

        String currentPassword = passwordChangeRequest.currentPassword();
        String newPassword = passwordChangeRequest.newPassword();
        String confirmation = passwordChangeRequest.confirmation();
        if ( currentPassword == null || !passwordEncoder.matches( currentPassword, panelUser.getPasswordHash() ) ) {
            return PasswordChangeResult.CURRENT_PASSWORD_INCORRECT;
        }
        if ( newPassword == null || !newPassword.equals( confirmation ) ) {
            return PasswordChangeResult.NEW_PASSWORD_MISMATCH;
        }
        if ( passwordEncoder.matches( newPassword, panelUser.getPasswordHash() ) ) {
            return PasswordChangeResult.NEW_PASSWORD_UNCHANGED;
        }

        panelUser.changePassword( passwordEncoder.encode( newPassword ) );
        log.info( "Panel user password was changed and the first-login restriction was removed" );
        return PasswordChangeResult.SUCCESS;
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
