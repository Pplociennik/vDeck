package com.github.pplociennik.vdeck.feature.authentication.service.impl;

import com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage;
import com.github.pplociennik.vdeck.feature.authentication.domain.PanelRole;
import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.dto.InitialPasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link PanelUserServiceImpl} maps persistent users for Spring Security, replaces
 * passwords only after all applicable checks succeed, and skips the current-password proof exactly for
 * an account that still has to leave the first-login state.
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
        underTest.changePassword( USERNAME, request );

        // THEN
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
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT ) );
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
                "admin", "Replacement-1", "different-password" );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_MISMATCH ) );
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
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_UNCHANGED ) );
        assertThat( panelUser.isPasswordChangeRequired() ).isTrue();
    }

    /**
     * Verifies that a replacement missing any of the required character kinds is rejected and leaves
     * the stored credentials untouched.
     *
     * @param weakPassword
     *         replacement long enough for annotation validation but missing one character kind
     */
    @ParameterizedTest( name = "[{index}] weakPassword={0}" )
    @ValueSource( strings = { "Haslo-bez-cyfry", "HASLO-BEZ-MALEJ1", "haslo-bez-duzej1", "HasloBezZnaku12" } )
    void shouldRejectPasswordChange_whenNewPasswordMissesRequiredCharacterKind( String weakPassword ) {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        PasswordChangeRequest request = new PasswordChangeRequest( "admin", weakPassword, weakPassword );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );
        given( passwordEncoder.matches( weakPassword, CURRENT_HASH ) ).willReturn( false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK ) );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
        assertThat( panelUser.isPasswordChangeRequired() ).isTrue();
    }

    /**
     * Verifies that whitespace alone does not pass for a special character, so a password built only
     * from letters, digits and spaces stays rejected.
     */
    @Test
    void shouldRejectPasswordChange_whenOnlySpecialCharacterIsWhitespace() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        String spacedPassword = "Haslo bez znaku 12";
        PasswordChangeRequest request = new PasswordChangeRequest( "admin", spacedPassword, spacedPassword );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.currentPassword(), CURRENT_HASH ) ).willReturn( true );
        given( passwordEncoder.matches( spacedPassword, CURRENT_HASH ) ).willReturn( false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK ) );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
    }

    /**
     * Verifies that replacing the initial password succeeds without the current password being supplied
     * or verified at all.
     */
    @Test
    void shouldChangeInitialPassword_whenReplacementIsValid() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        InitialPasswordChangeRequest request = validInitialRequest();
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.newPassword(), CURRENT_HASH ) ).willReturn( false );
        given( passwordEncoder.encode( request.newPassword() ) ).willReturn( "{bcrypt}replacement" );

        // WHEN
        underTest.changeInitialPassword( USERNAME, request );

        // THEN
        assertThat( panelUser.getPasswordHash() ).isEqualTo( "{bcrypt}replacement" );
        assertThat( panelUser.isPasswordChangeRequired() ).isFalse();
    }

    /**
     * Verifies that the initial replacement still refuses a confirmation that does not match.
     */
    @Test
    void shouldRejectInitialPasswordChange_whenConfirmationDoesNotMatch() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        InitialPasswordChangeRequest request = new InitialPasswordChangeRequest(
                "Replacement-1", "different-password" );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_MISMATCH ) );
        assertThat( panelUser.isPasswordChangeRequired() ).isTrue();
    }

    /**
     * Verifies that the initial replacement refuses to keep the published default password.
     */
    @Test
    void shouldRejectInitialPasswordChange_whenNewPasswordMatchesInitialPassword() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        InitialPasswordChangeRequest request = validInitialRequest();
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( request.newPassword(), CURRENT_HASH ) ).willReturn( true );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_UNCHANGED ) );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
    }

    /**
     * Verifies that the initial replacement enforces the same character-kind rule as an ordinary change.
     */
    @Test
    void shouldRejectInitialPasswordChange_whenNewPasswordMissesRequiredCharacterKind() {
        // GIVEN
        PanelUser panelUser = panelUser( true );
        String weakPassword = "haslo-bez-duzej1";
        InitialPasswordChangeRequest request = new InitialPasswordChangeRequest( weakPassword, weakPassword );
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser ) );
        given( passwordEncoder.matches( weakPassword, CURRENT_HASH ) ).willReturn( false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( USERNAME, request ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK ) );
        assertThat( panelUser.getPasswordHash() ).isEqualTo( CURRENT_HASH );
    }

    /**
     * Verifies that an account which already chose its own password cannot have it replaced without the
     * current password, even if the request reaches the service directly.
     */
    @Test
    void shouldThrowException_whenInitialPasswordWasAlreadyReplaced() {
        // GIVEN
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.of( panelUser( false ) ) );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.changeInitialPassword( USERNAME, validInitialRequest() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "Initial password replacement is not pending" );
    }

    /**
     * Verifies that an account removed while its session was alive produces the neutral lookup failure.
     */
    @Test
    void shouldThrowException_whenAccountDisappearedBeforeInitialPasswordChange() {
        // GIVEN
        given( userRepository.findById( USERNAME ) ).willReturn( Optional.empty() );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.changeInitialPassword( USERNAME, validInitialRequest() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UsernameNotFoundException.class )
                .hasMessageContaining( "Authenticated panel user was not found" );
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
     * Verifies that a null username is rejected before the initial replacement touches persistence.
     */
    @Test
    void shouldThrowException_whenUsernameOfInitialChangeIsNull() {
        // GIVEN
        // no username

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( null, validInitialRequest() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "username must not be null" );
    }

    /**
     * Verifies that a null initial-password request is rejected before persistent lookup.
     */
    @Test
    void shouldThrowException_whenInitialPasswordChangeRequestIsNull() {
        // GIVEN
        // no initial password-change request

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( USERNAME, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "initialPasswordChangeRequest must not be null" );
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
        return new PasswordChangeRequest( "admin", "Replacement-1", "Replacement-1" );
    }

    private InitialPasswordChangeRequest validInitialRequest() {
        return new InitialPasswordChangeRequest( "Replacement-1", "Replacement-1" );
    }
}