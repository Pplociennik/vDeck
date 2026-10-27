package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
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
 * Verifies the confirmed handoff of the installation lock. The current test process plays both the
 * predecessor and the successor, which keeps the test free of additional processes.
 */
class HandoffProtocolTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Duration SHORT = Duration.ofMillis( 300 );

    @TempDir
    private Path directory;

    private Path installation;

    private HandoffProtocol underTest;

    private HandoffProtocol successor;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a predecessor protocol and a successor protocol whose parent is the current process.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        ProcessHandle self = ProcessHandle.current();
        underTest = new HandoffProtocol( installation, Duration.ofMillis( 10 ), self, self.parent() );
        successor = new HandoffProtocol( installation, Duration.ofMillis( 10 ), self, Optional.of( self ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the complete handoff: descriptor, lock takeover, acknowledgement and one-time cleanup.
     */
    @Test
    void shouldHandOverLock_whenSuccessorAcceptsPublishedDescriptor() {
        // GIVEN
        InstallationLock lock = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
        String operationId = HandoffProtocol.newOperationId();
        ProcessIdentity identity = underTest.publish( operationId, HandoffRole.PANEL, ProcessHandle.current() );
        lock.close();

        // WHEN
        AcceptedHandoff accepted = successor.accept( operationId, HandoffRole.PANEL, SHORT );
        boolean acknowledged = underTest.awaitAcknowledgement( operationId, identity, SHORT );

        // THEN
        assertThat( acknowledged ).isTrue();
        assertThat( accepted.lock().isHeld() ).isTrue();
        assertThat( accepted.predecessor().pid() ).isEqualTo( ProcessHandle.current().pid() );
        assertThat( installation.resolve( ".vDeck/handoff" ) ).isEmptyDirectory();
        accepted.lock().close();
    }

    /**
     * Verifies that a descriptor written for another role never authorizes a successor.
     */
    @Test
    void shouldRefuseHandoff_whenRoleDiffers() {
        // GIVEN
        String operationId = HandoffProtocol.newOperationId();
        underTest.publish( operationId, HandoffRole.PANEL, ProcessHandle.current() );

        // WHEN
        var failure = catchThrowable( () -> successor.accept( operationId, HandoffRole.UPDATER, SHORT ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "role" );
        assertThat( InstallationLock.tryAcquire( installation, InstallationLock.Kind.INSTALLATION ) ).isPresent()
                .get().satisfies( InstallationLock::close );
    }

    /**
     * Verifies that a successor whose real parent is not the predecessor is refused.
     */
    @Test
    void shouldRefuseHandoff_whenParentIsAnotherProcess() {
        // GIVEN
        String operationId = HandoffProtocol.newOperationId();
        underTest.publish( operationId, HandoffRole.PANEL, ProcessHandle.current() );
        HandoffProtocol orphan = new HandoffProtocol( installation, Duration.ofMillis( 10 ), ProcessHandle.current(),
                Optional.empty() );

        // WHEN
        var failure = catchThrowable( () -> orphan.accept( operationId, HandoffRole.PANEL, SHORT ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "predecessor" );
    }

    /**
     * Verifies that a successor without a descriptor gives up after its timeout.
     */
    @Test
    void shouldThrowException_whenDescriptorNeverAppears() {
        // GIVEN
        String operationId = HandoffProtocol.newOperationId();

        // WHEN
        var failure = catchThrowable( () -> successor.accept( operationId, HandoffRole.PANEL, SHORT ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "did not appear" );
    }

    /**
     * Verifies that a successor cannot take a lock the predecessor still holds.
     */
    @Test
    void shouldThrowException_whenPredecessorKeepsLock() {
        // GIVEN
        InstallationLock lock = InstallationLock.acquire( installation, InstallationLock.Kind.INSTALLATION );
        String operationId = HandoffProtocol.newOperationId();
        underTest.publish( operationId, HandoffRole.PANEL, ProcessHandle.current() );

        // WHEN
        var failure = catchThrowable( () -> successor.accept( operationId, HandoffRole.PANEL, SHORT ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "release" );
        lock.close();
    }

    /**
     * Verifies that a missing acknowledgement ends in a refusal and removes the descriptor.
     */
    @Test
    void shouldReturnFalse_whenAcknowledgementIsMissing() {
        // GIVEN
        String operationId = HandoffProtocol.newOperationId();
        ProcessIdentity identity = underTest.publish( operationId, HandoffRole.PANEL, ProcessHandle.current() );

        // WHEN
        boolean acknowledged = underTest.awaitAcknowledgement( operationId, identity, SHORT );

        // THEN
        assertThat( acknowledged ).isFalse();
        assertThat( installation.resolve( ".vDeck/handoff" ) ).isEmptyDirectory();
    }

    /**
     * Verifies that a process that no longer exists counts as exited.
     */
    @Test
    void shouldReportExit_whenProcessDoesNotExist() {
        // GIVEN
        ProcessIdentity gone = new ProcessIdentity( 4_000_000_000L, Instant.EPOCH, installation.resolve( "x.exe" ) );

        // WHEN
        boolean exited = HandoffProtocol.awaitExit( gone, SHORT );

        // THEN
        assertThat( exited ).isTrue();
    }

    /**
     * Verifies that a reused PID with another creation time counts as exited, while a live process (the
     * build process running this test) does not exit within the timeout.
     */
    @Test
    void shouldDistinguishReusedPid_whenAwaitingExit() {
        // GIVEN
        ProcessHandle running = ProcessHandle.current().parent().orElseThrow();
        Path executable = Path.of( running.info().command().orElseThrow() ).toAbsolutePath().normalize();
        ProcessIdentity reused = new ProcessIdentity( running.pid(), Instant.EPOCH, executable );
        ProcessIdentity live = new ProcessIdentity( running.pid(), running.info().startInstant().orElseThrow(),
                executable );

        // WHEN
        boolean reusedExited = HandoffProtocol.awaitExit( reused, SHORT );
        boolean liveExited = HandoffProtocol.awaitExit( live, Duration.ofMillis( 50 ) );

        // THEN
        assertThat( reusedExited ).isTrue();
        assertThat( liveExited ).isFalse();
    }

    /**
     * Verifies that stale descriptors are removed by the owner of both locks.
     */
    @Test
    void shouldDeleteStaleFiles_whenDiscarding() {
        // GIVEN
        underTest.publish( HandoffProtocol.newOperationId(), HandoffRole.LAUNCHER, ProcessHandle.current() );

        // WHEN
        underTest.discardStale();

        // THEN
        assertThat( installation.resolve( ".vDeck/handoff" ) ).isEmptyDirectory();
    }

    /**
     * Verifies that a malformed identifier, which would become a file name, is rejected.
     */
    @Test
    void shouldThrowException_whenOperationIdIsMalformed() {
        // GIVEN
        String malformed = "..\\escape";

        // WHEN
        var failure = catchThrowable( () -> successor.accept( malformed, HandoffRole.PANEL, SHORT ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        String operationId = HandoffProtocol.newOperationId();

        // WHEN
        var missingInstallation = catchThrowable( () -> new HandoffProtocol( null ) );
        var missingRole = catchThrowable( () -> underTest.publish( operationId, null, ProcessHandle.current() ) );
        var missingId = catchThrowable( () -> successor.accept( null, HandoffRole.PANEL, SHORT ) );

        // THEN
        assertThat( missingInstallation ).isInstanceOf( NullPointerException.class );
        assertThat( missingRole ).isInstanceOf( NullPointerException.class );
        assertThat( missingId ).isInstanceOf( NullPointerException.class );
    }
}
