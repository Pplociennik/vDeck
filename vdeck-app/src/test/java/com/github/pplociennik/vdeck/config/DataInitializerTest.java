package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link DataInitializer} creates only a missing default administrator, stores an encoded
 * initial password and, on that fresh database, marks the installed version's change list as seen.
 */
@ExtendWith( MockitoExtension.class )
class DataInitializerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ReleaseNotesService releaseNotesService;

    private DataInitializer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh initializer using isolated repository and encoder collaborators.
     */
    @BeforeEach
    void setUp() {
        underTest = new DataInitializer( userRepository, passwordEncoder, releaseNotesService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a missing administrator is persisted with an encoded password, the ADMIN role
     * and mandatory password replacement, and that the fresh installation does not announce its version.
     */
    @Test
    void shouldCreateDefaultAdministrator_whenAccountDoesNotExist() throws Exception {
        // GIVEN
        given( userRepository.existsById( "admin" ) ).willReturn( false );
        given( passwordEncoder.encode( "admin" ) ).willReturn( "{bcrypt}encoded" );
        ArgumentCaptor< PanelUser > userCaptor = ArgumentCaptor.forClass( PanelUser.class );

        // WHEN
        underTest.run();

        // THEN
        verify( userRepository ).save( userCaptor.capture() );
        PanelUser savedUser = userCaptor.getValue();
        assertThat( savedUser.getUsername() ).isEqualTo( "admin" );
        assertThat( savedUser.getPasswordHash() ).isEqualTo( "{bcrypt}encoded" );
        assertThat( savedUser.getRole().name() ).isEqualTo( "ADMIN" );
        assertThat( savedUser.isPasswordChangeRequired() ).isTrue();
        verify( releaseNotesService ).acknowledgeInstalled();
    }

    /**
     * Verifies that an existing administrator is preserved without encoding or saving defaults, and that
     * an updated installation keeps its change list unacknowledged.
     */
    @Test
    void shouldPreserveExistingAdministrator_whenAccountExists() throws Exception {
        // GIVEN
        given( userRepository.existsById( "admin" ) ).willReturn( true );

        // WHEN
        underTest.run();

        // THEN
        verify( passwordEncoder, never() ).encode( "admin" );
        verify( userRepository, never() ).save( org.mockito.ArgumentMatchers.any() );
        verify( releaseNotesService, never() ).acknowledgeInstalled();
    }

    /**
     * Verifies that a repository failure is propagated so startup cannot continue without knowing
     * whether required credentials exist.
     */
    @Test
    void shouldPropagateException_whenRepositoryCheckFails() {
        // GIVEN
        given( userRepository.existsById( "admin" ) ).willThrow( new IllegalStateException( "database failed" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.run() );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "database failed" );
    }

    /**
     * Verifies that null application arguments are rejected before repository access.
     */
    @Test
    void shouldThrowException_whenArgumentsAreNull() {
        // GIVEN
        String[] arguments = null;

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.run( arguments ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "args must not be null" );
        verify( userRepository, never() ).existsById( org.mockito.ArgumentMatchers.any() );
    }

    /**
     * Verifies that a null repository is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenUserRepositoryIsNull() {
        // GIVEN
        // no repository

        // WHEN
        Throwable thrown = catchThrowable( () -> new DataInitializer( null, passwordEncoder, releaseNotesService ) );

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
        Throwable thrown = catchThrowable( () -> new DataInitializer( userRepository, null, releaseNotesService ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "passwordEncoder must not be null" );
    }

    /**
     * Verifies that a null release notes service is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenReleaseNotesServiceIsNull() {
        // GIVEN
        // no release notes service

        // WHEN
        Throwable thrown = catchThrowable( () -> new DataInitializer( userRepository, passwordEncoder, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "releaseNotesService must not be null" );
    }
}
