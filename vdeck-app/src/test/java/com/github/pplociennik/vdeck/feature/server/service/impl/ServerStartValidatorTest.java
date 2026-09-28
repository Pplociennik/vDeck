package com.github.pplociennik.vdeck.feature.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Verifies profile and constructor rejections before validation reaches any UDP probe.
 */
@ExtendWith( MockitoExtension.class )
class ServerStartValidatorTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String SAVE_DIRECTORY = "valheim-data";

    private static final String SERVER_NAME = "vDeck";

    private String password = "";

    @Mock
    private PathService pathService;

    @TempDir
    private Path installationDirectory;

    private Path executable;

    private ServerStartValidator underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the validator and a server executable that exists, so each test only has to break the
     * one thing it is about.
     *
     * @throws IOException
     *         if the stand-in executable cannot be created
     */
    @BeforeEach
    void setUp() throws IOException {
        executable = installationDirectory.resolve( "valheim_server.exe" );
        Files.createFile( executable );
        underTest = new ServerStartValidator( pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a missing server executable is refused, which is what an installation directory
     * without Valheim looks like.
     *
     * @throws IOException
     *         if the executable cannot be removed
     */
    @Test
    void shouldReject_whenExecutableIsMissing() throws IOException {
        // GIVEN
        Files.delete( executable );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( 2456 ), password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.EXECUTABLE_MISSING );
    }

    /**
     * Verifies that a directory standing where the executable should be is refused too, because it
     * cannot be launched either.
     *
     * @throws IOException
     *         if the directory cannot be prepared
     */
    @Test
    void shouldReject_whenExecutableIsADirectory() throws IOException {
        // GIVEN
        Files.delete( executable );
        Files.createDirectory( executable );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( 2456 ), password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.EXECUTABLE_MISSING );
    }

    /**
     * Verifies that a blank mandatory setting is refused, which is how a hand-edited settings file can
     * leave the profile.
     */
    @Test
    void shouldReject_whenServerNameIsBlank() {
        // GIVEN
        LaunchProfile profile = profile( "   ", "Dedicated", SAVE_DIRECTORY, 2456, false );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.PROFILE_INCOMPLETE );
    }

    /**
     * Verifies that a blank world name is refused for the same reason.
     */
    @Test
    void shouldReject_whenWorldNameIsBlank() {
        // GIVEN
        LaunchProfile profile = profile( SERVER_NAME, "", SAVE_DIRECTORY, 2456, false );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.PROFILE_INCOMPLETE );
    }

    /**
     * Verifies Valheim's own rule that a password shorter than five characters is not accepted.
     */
    @Test
    void shouldReject_whenPasswordIsTooShort() {
        // GIVEN
        givenPassword( "abcd" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( 2456 ), password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.PASSWORD_TOO_SHORT );
    }

    /**
     * Verifies Valheim's own rule that the password must not occur inside the server name, including
     * when the two differ only in letter case.
     */
    @Test
    void shouldReject_whenPasswordOccursInServerName() {
        // GIVEN
        givenPassword( "secret" );
        LaunchProfile profile = profile( "My SECRET server", "Dedicated", SAVE_DIRECTORY, 2456, false );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.PASSWORD_PART_OF_NAME );
    }

    /**
     * Verifies Valheim's own rule that a server listed publicly must have a password.
     */
    @Test
    void shouldReject_whenPublicServerHasNoPassword() {
        // GIVEN
        givenPassword( "" );
        LaunchProfile profile = profile( SERVER_NAME, "Dedicated", SAVE_DIRECTORY, 2456, true );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.PUBLIC_REQUIRES_PASSWORD );
    }

    /**
     * Verifies that a null profile is rejected before any check reads from it.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.validate( null, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "configuration must not be null" );
    }

    /**
     * Verifies that a null executable is rejected.
     */
    @Test
    void shouldThrowException_whenExecutableIsNull() {
        // GIVEN
        // no executable

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( 2456 ), password ), null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "executable must not be null" );
    }

    /**
     * Verifies that the validator cannot be created without path resolution.
     */
    @Test
    void shouldThrowException_whenPathServiceIsNull() {
        // GIVEN
        // no path service

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerStartValidator( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "pathService must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Asserts that the start was refused by a rule, with the expected reason and without a technical
     * cause.
     *
     * @param thrown
     *         exception the validator ended with
     * @param expected
     *         reason the administrator should see
     */
    private static void assertThatRejectedWith( Throwable thrown, ServerMessage expected ) {
        assertThat( thrown ).isInstanceOfSatisfying( ServerStartException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( expected );
            assertThat( exception.isFailure() ).isFalse();
        } );
    }

    /**
     * Sets the password captured together with the profile.
     *
     * @param password
     *         password included in the configuration
     */
    private void givenPassword( String password ) {
        this.password = password;
    }

    private static LaunchProfile privateProfile( int port ) {
        return profile( SERVER_NAME, "Dedicated", SAVE_DIRECTORY, port, false );
    }

    private static LaunchProfile profile( String serverName, String worldName, String saveDirectory, int port,
                                          boolean publicServer ) {
        return new LaunchProfile( serverName, port, worldName, false, publicServer, false, "",
                saveDirectory, 1800, 4, 7200, 43200, null, Map.of(), Set.of() );
    }
}
