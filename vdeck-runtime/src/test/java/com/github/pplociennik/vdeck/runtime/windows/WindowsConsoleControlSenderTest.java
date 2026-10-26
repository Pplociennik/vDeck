package com.github.pplociennik.vdeck.runtime.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.LongFunction;

/** Verifies isolated console signaling and cleanup against fake Windows and process boundaries. */
@ExtendWith( MockitoExtension.class )
class WindowsConsoleControlSenderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    @Mock
    private Win32 nativeCalls;

    @Mock
    private LongFunction< Optional< ProcessHandle > > lookup;

    @Mock
    private ProcessHandle handle;

    @Mock
    private ProcessHandle.Info information;

    private ProcessIdentity identity;

    private WindowsConsoleControlSender underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a fresh sender and an identity backed by a temporary executable file. */
    @BeforeEach
    void setUp() throws IOException {
        identity = new ProcessIdentity( 42, Instant.parse( "2026-10-07T12:00:00Z" ),
                Files.createFile( directory.resolve( "server.exe" ) ).toRealPath() );
        underTest = new WindowsConsoleControlSender( nativeCalls, lookup );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies console and process boundaries cannot be omitted during construction.
     *
     * @param missingBoundary
     *         dependency to omit
     */
    @ParameterizedTest( name = "[{index}] missing sender boundary {0}" )
    @ValueSource( ints = { 0, 1 } )
    void shouldRejectConstruction_whenBoundaryIsNull( int missingBoundary ) {
        // GIVEN
        // Remaining dependencies come from setup.

        // WHEN
        var thrown = catchThrowable( () -> new WindowsConsoleControlSender(
                missingBoundary == 0 ? null : nativeCalls, missingBoundary == 1 ? null : lookup ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class );
        verifyNoInteractions( nativeCalls, lookup );
    }

    /** Verifies attach, handler registration, second identity check, send and guaranteed detach ordering. */
    @Test
    void shouldSendAndDetach_whenIdentityMatches() {
        // GIVEN
        givenVerifiedDestination();
        givenAttachedConsole();
        given( nativeCalls.sendConsoleControl() ).willReturn( 1 );

        // WHEN
        underTest.send( identity );

        // THEN
        var order = inOrder( nativeCalls );
        order.verify( nativeCalls ).freeConsole();
        order.verify( nativeCalls ).attachConsole( 42 );
        order.verify( nativeCalls ).ignoreConsoleControl();
        order.verify( nativeCalls ).sendConsoleControl();
        order.verify( nativeCalls ).freeConsole();
        verify( handle, times( 2 ) ).info();
    }

    /** Verifies that absent processes do not cause any change to the helper's console. */
    @Test
    void shouldRejectSend_whenDestinationIsAbsent() {
        // GIVEN
        given( lookup.apply( 42 ) ).willReturn( Optional.empty() );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
        verifyNoInteractions( nativeCalls );
    }

    /** Verifies rejection of a different PID before any console call. */
    @Test
    void shouldRejectSend_whenDestinationIdentityDiffers() {
        // GIVEN
        given( lookup.apply( 42 ) ).willReturn( Optional.of( handle ) );
        given( handle.pid() ).willReturn( 43L );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
        verifyNoInteractions( nativeCalls );
    }

    /** Verifies that failure to detach the initial console stops before attachment. */
    @Test
    void shouldKeepWindowsError_whenInitialDetachmentFails() {
        // GIVEN
        givenVerifiedDestination();
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class ).hasMessageContaining( "FreeConsole" )
                .hasMessageContaining( "5" );
        verify( nativeCalls, never() ).attachConsole( 42 );
    }

    /** Verifies that failed attachment does not proceed to handler registration or send. */
    @Test
    void shouldStopBeforeSignal_whenAttachmentFails() {
        // GIVEN
        givenVerifiedDestination();
        given( nativeCalls.freeConsole() ).willReturn( 1 );
        given( nativeCalls.getLastError() ).willReturn( 6 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class ).hasMessageContaining( "AttachConsole" );
        verify( nativeCalls, never() ).ignoreConsoleControl();
        verify( nativeCalls, times( 1 ) ).freeConsole();
    }

    /** Verifies cleanup of an attached console when registering CTRL+C suppression fails. */
    @Test
    void shouldDetachWithoutSending_whenHandlerRegistrationFails() {
        // GIVEN
        givenVerifiedDestination();
        given( nativeCalls.freeConsole() ).willReturn( 1 );
        given( nativeCalls.attachConsole( 42 ) ).willReturn( 1 );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "SetConsoleCtrlHandler" );
        verify( nativeCalls, times( 2 ) ).freeConsole();
        verify( nativeCalls, never() ).sendConsoleControl();
    }

    /** Verifies a destination dying after attachment is rechecked and never receives a signal. */
    @Test
    void shouldDetachWithoutSending_whenDestinationDiesAfterAttachment() {
        // GIVEN
        givenVerifiedDestination();
        givenAttachedConsole();
        given( handle.isAlive() ).willReturn( true, false );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
        verify( nativeCalls, times( 2 ) ).freeConsole();
        verify( nativeCalls, never() ).sendConsoleControl();
    }

    /** Verifies that a failed send preserves its native error and detaches from the console. */
    @Test
    void shouldKeepWindowsError_whenSignalFails() {
        // GIVEN
        givenVerifiedDestination();
        givenAttachedConsole();
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "GenerateConsoleCtrlEvent" );
        verify( nativeCalls, times( 2 ) ).freeConsole();
    }

    /** Verifies cleanup failure is attached to the original signal failure. */
    @Test
    void shouldPreserveBothFailures_whenSignalAndCleanupFail() {
        // GIVEN
        givenVerifiedDestination();
        givenAttachedConsole();
        given( nativeCalls.freeConsole() ).willReturn( 1, 0 );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "GenerateConsoleCtrlEvent" );
        assertThat( thrown.getSuppressed() ).singleElement().satisfies( failure ->
                assertThat( failure ).hasMessageContaining( "cleanup" ) );
    }

    /** Verifies that successful signaling does not hide a cleanup error. */
    @Test
    void shouldReportCleanupFailure_whenSignalSucceeded() {
        // GIVEN
        givenVerifiedDestination();
        givenAttachedConsole();
        given( nativeCalls.freeConsole() ).willReturn( 1, 0 );
        given( nativeCalls.sendConsoleControl() ).willReturn( 1 );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( identity ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class ).hasMessageContaining( "cleanup" );
    }

    /** Verifies null input causes no operating-system action. */
    @Test
    void shouldRejectNull_whenIdentityIsMissing() {
        // GIVEN
        // Null identity.

        // WHEN
        var thrown = catchThrowable( () -> underTest.send( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class );
        verifyNoInteractions( lookup, nativeCalls );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenVerifiedDestination() {
        given( lookup.apply( 42 ) ).willReturn( Optional.of( handle ) );
        given( handle.pid() ).willReturn( 42L );
        given( handle.info() ).willReturn( information );
        given( handle.isAlive() ).willReturn( true );
        given( information.startInstant() ).willReturn( Optional.of( identity.startedAt() ) );
        given( information.command() ).willReturn( Optional.of( identity.executable().toString() ) );
    }

    private void givenAttachedConsole() {
        given( nativeCalls.freeConsole() ).willReturn( 1 );
        given( nativeCalls.attachConsole( 42 ) ).willReturn( 1 );
        given( nativeCalls.ignoreConsoleControl() ).willReturn( 1 );
    }
}
