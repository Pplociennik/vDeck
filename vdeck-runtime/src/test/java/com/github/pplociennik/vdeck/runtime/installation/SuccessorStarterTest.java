package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the failure handling of {@link SuccessorStarter} without starting real processes. The complete
 * handoff between real processes is covered by {@link HandoffProtocolTest} and the distribution test.
 */
class SuccessorStarterTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path installation;

    private InstallationLock lock;

    private List< List< String > > commands;

    private SuccessorStarter underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a starter whose process factory records the command and fails to start.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        lock = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
        commands = new ArrayList<>();
        SuccessorStarter.ProcessFactory failing = ( command, workingDirectory ) -> {
            commands.add( command );
            throw new IOException( "cannot start" );
        };
        underTest = new SuccessorStarter( installation, new HandoffProtocol( installation ), failing,
                Duration.ofMillis( 100 ) );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Releases the lock of the fixture.
     */
    @AfterEach
    void tearDown() {
        lock.close();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a successor that cannot start leaves the caller holding its lock, and that the command
     * received the handoff identifier.
     */
    @Test
    void shouldKeepLock_whenSuccessorCannotStart() {
        // GIVEN
        // The process factory always fails.

        // WHEN
        HandoffResult result = underTest.handOff( lock, HandoffRole.PANEL, operationId -> List.of( "java", operationId ) );

        // THEN
        assertThat( result ).isInstanceOfSatisfying( HandoffResult.Failed.class,
                failed -> assertThat( failed.lock() ).containsSame( lock ) );
        assertThat( lock.isHeld() ).isTrue();
        assertThat( commands ).singleElement().satisfies( command -> assertThat( command.get( 1 ) ).hasSize( 36 ) );
    }

    /**
     * Verifies that only a held installation lock can be handed over.
     */
    @Test
    void shouldThrowException_whenLockIsNotHeld() {
        // GIVEN
        lock.close();

        // WHEN
        var failure = catchThrowable( () -> underTest.handOff( lock, HandoffRole.PANEL, operationId -> List.of() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
        assertThat( commands ).isEmpty();
    }

    /**
     * Verifies that the startup gate can never be handed over.
     */
    @Test
    void shouldThrowException_whenLockIsTheGate() {
        // GIVEN
        InstallationLock gate = InstallationLock.acquire( installation, InstallationLock.Kind.STARTUP_GATE );

        // WHEN
        var failure = catchThrowable( () -> underTest.handOff( gate, HandoffRole.PANEL, operationId -> List.of() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
        gate.close();
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingRole = catchThrowable( () -> underTest.handOff( lock, null, operationId -> List.of() ) );
        var missingCommand = catchThrowable( () -> underTest.handOff( lock, HandoffRole.PANEL, null ) );

        // THEN
        assertThat( missingRole ).isInstanceOf( NullPointerException.class );
        assertThat( missingCommand ).isInstanceOf( NullPointerException.class );
    }
}
