package com.github.pplociennik.vdeck.feature.authentication.service.impl;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelRole;
import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.domain.PasswordChangeResult;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link PanelUserServiceImpl} maps persistent users for Spring Security and replaces
 * passwords only after all credential-dependent checks succeed.
 */
@ExtendWith( MockitoExtension.class )
class PanelUserServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String USERNAME = "admin";

    private static final String CURRENT_HASH = "{bcrypt}current";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PanelUserServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh panel-user service with isolated persistence and encoding collaborators.
     */
    @BeforeEach
    void setUp() {
        underTest = new PanelUserServiceImpl( userRepository, passwordEncoder );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a first-login administrator is mapped with both its role and temporary
     * password-change authority.
     */
    @Test
    void shouldIncludePasswordChangeAuthority_whenInitialPasswordIsStillActive() {
        // GIVEN
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser( true ) ) );

        // WHEN
        var result = underTest.loadUserByUsername( USERNAME );

        // THEN
        assertThat( result.getUsername() ).isEqualTo( USERNAME );
        assertThat( result.getPassword() ).isEqualTo( CURRENT_HASH );
        assertThat( result.getAuthorities() )
                .extracting( "authority" )
                .containsExactlyInAnyOrder( "ROLE_ADMIN", PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY );
    }

    /**
     * Verifies that a user who replaced the initial password receives only the persisted role.
     */
    @Test
    void shouldExcludePasswordChangeAuthority_whenInitialPasswordWasReplaced() {
        // GIVEN
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser( false ) ) );

        // WHEN
        var result = underTest.loadUserByUsername( USERNAME );

        // THEN
        assertThat( result.getAuthorities() )
                .extracting( "authority" )
                .containsExactly( "ROLE_ADMIN" );
    }

    /**
     * Verifies that an unknown login name produces Spring Security's neutral user-not-found failure.
     */
    @Test
    void shouldThrowException_whenUserDoesNotExist() {
        // GIVEN
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.empty() );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.loadUserByUsername( USERNAME ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UsernameNotFoundException.class )
                .hasMessageContaining( "Panel user was not found" );
    }

    /**
     * Verifies that a valid request replaces the encoded password and removes the first-login flag.
     */
    @Test
    void shouldChangePassword_whenCredentialsAndConfirmationAreValid() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        PasswordChangeRequest request = validRequest();
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );
        given( passwordEncoder.matches( request.newPassword(), CURRENT_HASH ) ).willReturn( false );
        given( passwordEncoder.encode( request.newPassword() ) ).willReturn( "{bcrypt}replacement" );

        // WHEN
        PasswordChangeResult result = underTest.changePassword( USERNAME, request );

        // THEN
        assertThat( result ).isEqualTo( PasswordChangeResult.SUCCESS );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( "{bcrypt}replacement" );
        assertThat( panelUser.isPasswordChangeRequired() ).isFalse();
    }

    /**
     * Verifies that an incorrect current password is rejected before replacement values are evaluated.
     */
    @Test
    void shouldRejectPasswordChange_whenCurrentPasswordIsIncorrect() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        PasswordChangeRequest request = validRequest();
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( false );

        // WHEN
        PasswordChangeResult result = underTest.changePassword( USERNAME, request );

        // THEN
        assertThat( result ).isEqualTo( PasswordChangeResult.CURRENT_PASSWORD_INCORRECT );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
    }

    /**
     * Verifies that differing replacement and confirmation values cannot change persisted credentials.
     */
    @Test
    void shouldRejectPasswordChange_whenConfirmationDoesNotMatch() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        PasswordChangeRequest request = new PasswordChangeRequest(
                "admin", "replacement-password", "different-password" );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );

        // WHEN
        PasswordChangeResult result = underTest.changePassword( USERNAME, request );

        // THEN
        assertThat( result ).isEqualTo( PasswordChangeResult.NEW_PASSWORD_MISMATCH );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
    }

    /**
     * Verifies that reusing the current password is rejected even though it has a different plaintext
     * representation than the salted stored hash.
     */
    @Test
    void shouldRejectPasswordChange_whenNewPasswordMatchesCurrentPassword() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        PasswordChangeRequest request = validRequest();
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );
        given( passwordEncoder.matches( request.newPassword(), CURRENT_HASH ) ).willReturn( true );

        // WHEN
        PasswordChangeResult result = underTest.changePassword( USERNAME, request );

        // THEN
        assertThat( result ).isEqualTo( PasswordChangeResult.NEW_PASSWORD_UNCHANGED );
        assertThat( panelUser.isPasswordChangeRequired() ).isTrue();
    }

    /**
     * Verifies that a null username is rejected before persistent lookup.
     */
    @Test
    void shouldThrowException_whenUsernameIsNull() {
        // GIVEN
        // no username

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( null, validRequest() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "username must not be null" );
    }

    /**
     * Verifies that a null password request is rejected before persistent lookup.
     */
    @Test
    void shouldThrowException_whenPasswordChangeRequestIsNull() {
        // GIVEN
        // no password-change request

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "passwordChangeRequest must not be null" );
    }

    /**
     * Verifies that a null repository is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenUserRepositoryIsNull() {
        // GIVEN
        // no repository

        // WHEN
        Throwable thrown = catchThrowable( () -> new PanelUserServiceImpl( null, passwordEncoder ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "userRepository must not be null" );
    }

    /**
     * Verifies that a null encoder is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenPasswordEncoderIsNull() {
        // GIVEN
        // no password encoder

        // WHEN
        Throwable thrown = catchThrowable( () -> new PanelUserServiceImpl( userRepository, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "passwordEncoder must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private PanelUser panelUser( boolean passwordChangeRequired ) {
        return new PanelUser( USERNAME, CURRENT_HASH, PanelRole.ADMIN, passwordChangeRequired );
    }

    private PasswordChangeRequest validRequest() {
        return new PasswordChangeRequest( "admin", "replacement-password", "replacement-password" );
    }
}
