package com.github.pplociennik.vdeck.feature.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Verifies usable profiles, occupied UDP ports and creation of the world directory during validation.
 *
 * <p>These are integration tests because validation probes real local UDP sockets. Files remain
 * isolated in a temporary directory and no external service or server process is used.
 */
@ExtendWith( MockitoExtension.class )
class ServerStartValidatorIntegrationTest {

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
     * Verifies that a complete, private profile with a free port and a writable world directory passes
     * every check.
     */
    @Test
    void shouldPass_whenProfileIsUsable() {
        // GIVEN
        givenPassword( "" );
        givenResolvedSaveDirectory();

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( freePort() ), password ), executable ) );

        // THEN
        assertThat( thrown ).isNull();
    }

    /**
     * Verifies that a password of exactly the minimum length is accepted, so the boundary is not off by
     * one in the strict direction.
     */
    @Test
    void shouldPass_whenPasswordIsExactlyMinimumLength() {
        // GIVEN
        givenPassword( "abcde" );
        givenResolvedSaveDirectory();

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( freePort() ), password ), executable ) );

        // THEN
        assertThat( thrown ).isNull();
    }

    /**
     * Verifies that an occupied port is refused instead of letting the server start and then fail to
     * become reachable.
     *
     * @throws IOException
     *         if the probe socket cannot be opened
     */
    @Test
    void shouldReject_whenConfiguredPortIsTaken() throws IOException {
        // GIVEN
        givenPassword( "" );
        try ( DatagramSocket occupied = new DatagramSocket( 0 ) ) {
            LaunchProfile profile = privateProfile( occupied.getLocalPort() );

            // WHEN
            Throwable thrown = catchThrowable(
                    () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

            // THEN
            assertThatRejectedWith( thrown, ServerMessage.PORT_IN_USE );
        }
    }

    /**
     * Verifies that the second port is checked as well, since Valheim binds the configured port and the
     * one above it.
     *
     * @throws IOException
     *         if the probe socket cannot be opened
     */
    @Test
    void shouldReject_whenPortAboveConfiguredOneIsTaken() throws IOException {
        // GIVEN
        givenPassword( "" );
        try ( DatagramSocket occupied = new DatagramSocket( 0 ) ) {
            LaunchProfile profile = privateProfile( occupied.getLocalPort() - 1 );

            // WHEN
            Throwable thrown = catchThrowable(
                    () -> underTest.validate( new LaunchConfiguration( profile, password ), executable ) );

            // THEN
            assertThatRejectedWith( thrown, ServerMessage.PORT_IN_USE );
        }
    }

    /**
     * Verifies that a world directory the panel refuses to resolve is reported as unusable rather than
     * letting a path outside the installation through.
     */
    @Test
    void shouldReject_whenSaveDirectoryLeavesInstallation() {
        // GIVEN
        givenPassword( "" );
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) )
                .willThrow( new PathResolutionException( "leaves the installation directory" ) );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( freePort() ), password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.SAVE_DIRECTORY_NOT_WRITABLE );
    }

    /**
     * Verifies that a world directory blocked by a file of the same name is refused, because it cannot
     * be created.
     *
     * @throws IOException
     *         if the blocking file cannot be created
     */
    @Test
    void shouldReject_whenSaveDirectoryCannotBeCreated() throws IOException {
        // GIVEN
        givenPassword( "" );
        Path blocked = installationDirectory.resolve( "blocked" );
        Files.createFile( blocked );
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) ).willReturn( blocked.resolve( "worlds" ) );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.validate( new LaunchConfiguration( privateProfile( freePort() ), password ), executable ) );

        // THEN
        assertThatRejectedWith( thrown, ServerMessage.SAVE_DIRECTORY_NOT_WRITABLE );
    }

    /**
     * Verifies that the world directory is created when it is missing, since the server puts the world
     * there on its first run.
     */
    @Test
    void shouldCreateSaveDirectory_whenItDoesNotExist() {
        // GIVEN
        givenPassword( "" );
        Path saveDirectory = installationDirectory.resolve( SAVE_DIRECTORY );
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) ).willReturn( saveDirectory );

        // WHEN
        underTest.validate( new LaunchConfiguration( privateProfile( freePort() ), password ), executable );

        // THEN
        assertThat( saveDirectory ).isDirectory();
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

    /**
     * Resolves the world directory inside the temporary installation directory.
     */
    private void givenResolvedSaveDirectory() {
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) )
                .willReturn( installationDirectory.resolve( SAVE_DIRECTORY ) );
    }

    /**
     * Asks the operating system for a port that is free right now, so the test never depends on what
     * else happens to be listening.
     *
     * @return a port number that was free a moment ago
     */
    private static int freePort() {
        for ( int attempt = 0; attempt < 100; attempt++ ) {
            try ( DatagramSocket probe = new DatagramSocket( 0 ) ) {
                int port = probe.getLocalPort();
                if ( port >= 65535 ) {
                    continue;
                }
                try ( DatagramSocket neighbour = new DatagramSocket( port + 1 ) ) {
                    return neighbour.getLocalPort() - 1;
                } catch ( IOException occupied ) {
                    // A free first port does not imply that its neighbour is also free.
                }
            } catch ( IOException exception ) {
                throw new IllegalStateException( "Could not find a free UDP port", exception );
            }
        }
        throw new IllegalStateException( "Could not find a free UDP port pair" );
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
