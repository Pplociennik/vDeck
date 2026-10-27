package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies how a launcher or updater takes ownership of both coordination locks.
 */
class InstallationOwnershipTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path installation;

    private InstallationOwnership underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a canonical installation.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Releases any ownership taken by a test.
     */
    @AfterEach
    void tearDown() {
        if ( underTest != null ) {
            underTest.close();
        }
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a free installation yields both locks, released together on close.
     */
    @Test
    void shouldTakeBothLocks_whenInstallationIsFree() {
        // GIVEN
        // Nobody owns the installation.

        // WHEN
        underTest = InstallationOwnership.tryTake( installation ).orElseThrow();

        // THEN
        assertThat( underTest.gate().isHeld() ).isTrue();
        assertThat( underTest.lock().isHeld() ).isTrue();
        underTest.close();
        assertThat( InstallationOwnership.tryTake( installation ) ).isPresent().get()
                .satisfies( InstallationOwnership::close );
    }

    /**
     * Verifies that a running panel, which holds only the installation lock, blocks a start and that the
     * gate is released again.
     */
    @Test
    void shouldReleaseGate_whenInstallationLockIsBusy() {
        // GIVEN
        InstallationLock panel = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );

        // WHEN
        Optional< InstallationOwnership > ownership = InstallationOwnership.tryTake( installation );

        // THEN
        assertThat( ownership ).isEmpty();
        assertThat( InstallationLock.tryAcquire( installation, InstallationLock.Kind.STARTUP_GATE ) ).isPresent()
                .get().satisfies( InstallationLock::close );
        panel.close();
    }

    /**
     * Verifies that a start in progress, which holds the gate, blocks another start.
     */
    @Test
    void shouldRefuse_whenGateIsBusy() {
        // GIVEN
        InstallationLock gate = InstallationLock.acquire( installation, InstallationLock.Kind.STARTUP_GATE );

        // WHEN
        Optional< InstallationOwnership > ownership = InstallationOwnership.tryTake( installation );

        // THEN
        assertThat( ownership ).isEmpty();
        gate.close();
    }

    /**
     * Verifies that after a handoff from an exited predecessor the successor also takes the gate.
     */
    @Test
    void shouldTakeGate_whenPredecessorHasExited() {
        // GIVEN
        InstallationLock lock = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
        AcceptedHandoff accepted = new AcceptedHandoff( lock, exitedProcess() );

        // WHEN
        underTest = InstallationOwnership.afterHandoff( accepted, Duration.ofMillis( 300 ) );

        // THEN
        assertThat( underTest.gate().isHeld() ).isTrue();
        assertThat( underTest.lock() ).isSameAs( lock );
    }

    /**
     * Verifies that a gate that stays busy fails the takeover and releases the installation lock.
     */
    @Test
    void shouldReleaseLock_whenGateStaysBusy() {
        // GIVEN
        InstallationLock gate = InstallationLock.acquire( installation, InstallationLock.Kind.STARTUP_GATE );
        InstallationLock lock = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
        AcceptedHandoff accepted = new AcceptedHandoff( lock, exitedProcess() );

        // WHEN
        var failure = catchThrowable( () -> InstallationOwnership.afterHandoff( accepted, Duration.ofMillis( 200 ) ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "gate" );
        assertThat( lock.isHeld() ).isFalse();
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
        var missingInstallation = catchThrowable( () -> InstallationOwnership.tryTake( null ) );
        var missingHandoff = catchThrowable( () -> InstallationOwnership.afterHandoff( null, Duration.ZERO ) );

        // THEN
        assertThat( missingInstallation ).isInstanceOf( NullPointerException.class );
        assertThat( missingHandoff ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private ProcessIdentity exitedProcess() {
        return new ProcessIdentity( 4_000_000_000L, Instant.EPOCH, installation.resolve( "launcher.exe" ) );
    }
}
