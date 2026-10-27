package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the exclusive coordination locks of an installation.
 */
class InstallationLockTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path installation;

    private InstallationLock underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a canonical installation and takes its installation lock.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        underTest = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Releases the lock of the fixture.
     */
    @AfterEach
    void tearDown() {
        underTest.close();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an acquired lock reports its installation, kind and held state and creates its file.
     */
    @Test
    void shouldDescribeHeldLock_whenAcquired() {
        // GIVEN
        // The fixture holds the installation lock.

        // WHEN
        boolean held = underTest.isHeld();

        // THEN
        assertThat( held ).isTrue();
        assertThat( underTest.getInstallation() ).isEqualTo( installation );
        assertThat( underTest.getKind() ).isEqualTo( InstallationLock.Kind.INSTALLATION );
        assertThat( installation.resolve( ".vDeck/vdeck.lock" ) ).isRegularFile();
    }

    /**
     * Verifies that a second owner of the same lock is refused without waiting.
     */
    @Test
    void shouldReturnEmpty_whenLockIsAlreadyHeld() {
        // GIVEN
        // The fixture holds the installation lock.

        // WHEN
        Optional< InstallationLock > second = InstallationLock.tryAcquire( installation,
                InstallationLock.Kind.INSTALLATION );

        // THEN
        assertThat( second ).isEmpty();
    }

    /**
     * Verifies that the startup gate is independent of the installation lock.
     */
    @Test
    void shouldAcquireStartupGate_whenOnlyInstallationLockIsHeld() {
        // GIVEN
        // The fixture holds the installation lock only.

        // WHEN
        Optional< InstallationLock > gate = InstallationLock.tryAcquire( installation, InstallationLock.Kind.STARTUP_GATE );

        // THEN
        assertThat( gate ).isPresent();
        assertThat( installation.resolve( ".vDeck/startup.lock" ) ).isRegularFile();
        gate.get().close();
    }

    /**
     * Verifies that a released lock can be acquired again and that closing twice is harmless.
     */
    @Test
    void shouldAllowNewOwner_whenLockIsReleased() {
        // GIVEN
        underTest.close();
        underTest.close();

        // WHEN
        Optional< InstallationLock > next = InstallationLock.tryAcquire( installation, InstallationLock.Kind.INSTALLATION );

        // THEN
        assertThat( underTest.isHeld() ).isFalse();
        assertThat( next ).isPresent();
        underTest = next.get();
    }

    /**
     * Verifies that acquiring a busy lock reports the owner conflict.
     */
    @Test
    void shouldThrowException_whenAcquiringBusyLock() {
        // GIVEN
        // The fixture holds the installation lock.

        // WHEN
        var failure = catchThrowable( () -> InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "installation lock" );
    }

    /**
     * Verifies that a relative installation is rejected.
     */
    @Test
    void shouldThrowException_whenInstallationIsRelative() {
        // GIVEN
        Path relative = Path.of( "relative" );

        // WHEN
        var failure = catchThrowable( () -> InstallationLock.tryAcquire( relative, InstallationLock.Kind.INSTALLATION ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingInstallation = catchThrowable( () -> InstallationLock.tryAcquire( null,
                InstallationLock.Kind.INSTALLATION ) );
        var missingKind = catchThrowable( () -> InstallationLock.tryAcquire( installation, null ) );

        // THEN
        assertThat( missingInstallation ).isInstanceOf( NullPointerException.class );
        assertThat( missingKind ).isInstanceOf( NullPointerException.class );
    }
}
