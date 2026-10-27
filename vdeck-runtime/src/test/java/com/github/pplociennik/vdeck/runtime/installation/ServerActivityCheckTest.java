package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Verifies the conservative detection of a running Valheim server.
 */
class ServerActivityCheckTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path installation;

    private Path executable;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation with a server executable.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        executable = Files.createFile( installation.resolve( "valheim_server.exe" ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that no token and no matching process means the server is stopped.
     */
    @Test
    void shouldReportInactive_whenNoTokenAndNoProcessExist() {
        // GIVEN
        ProcessHandle other = process( installation.resolve( "other.exe" ).toString() );
        ServerActivityCheck underTest = new ServerActivityCheck( installation, () -> Stream.of( other ) );

        // WHEN
        boolean active = underTest.isServerPossiblyActive();

        // THEN
        assertThat( active ).isFalse();
    }

    /**
     * Verifies that a recovery token alone blocks an update.
     */
    @Test
    void shouldReportActive_whenRecoveryTokenExists() throws Exception {
        // GIVEN
        Files.createDirectories( installation.resolve( ".vDeck" ) );
        Files.writeString( installation.resolve( ".vDeck/server.pid" ), "pid=1" );
        ServerActivityCheck underTest = new ServerActivityCheck( installation, Stream::empty );

        // WHEN
        boolean active = underTest.isServerPossiblyActive();

        // THEN
        assertThat( active ).isTrue();
    }

    /**
     * Verifies that a running server executable of this installation blocks an update.
     */
    @Test
    void shouldReportActive_whenServerProcessOfInstallationRuns() {
        // GIVEN
        ProcessHandle server = process( executable.toString() );
        ServerActivityCheck underTest = new ServerActivityCheck( installation, () -> Stream.of( server ) );

        // WHEN
        boolean active = underTest.isServerPossiblyActive();

        // THEN
        assertThat( active ).isTrue();
    }

    /**
     * Verifies that a failed check is treated as a running server.
     */
    @Test
    void shouldReportActive_whenProcessListFails() {
        // GIVEN
        ServerActivityCheck underTest = new ServerActivityCheck( installation, () -> {
            throw new UnsupportedOperationException( "denied" );
        } );

        // WHEN
        boolean active = underTest.isServerPossiblyActive();

        // THEN
        assertThat( active ).isTrue();
    }

    /**
     * Verifies that a missing installation is rejected.
     */
    @Test
    void shouldThrowException_whenInstallationIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> new ServerActivityCheck( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static ProcessHandle process( String command ) {
        ProcessHandle handle = mock( ProcessHandle.class );
        ProcessHandle.Info info = mock( ProcessHandle.Info.class );
        given( handle.info() ).willReturn( info );
        given( info.command() ).willReturn( Optional.of( command ) );
        return handle;
    }
}
