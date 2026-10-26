package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

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

/** Verifies identity matching and unavailable metadata without inspecting real processes. */
@ExtendWith( MockitoExtension.class )
class ProcessIdentityTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Instant CREATED_AT = Instant.parse( "2026-10-07T12:00:00Z" );

    @TempDir
    private Path directory;

    @Mock
    private ProcessHandle handle;

    @Mock
    private ProcessHandle.Info information;

    private Path executable;

    private ProcessIdentity underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a real temporary executable path and a fresh identity for every test. */
    @BeforeEach
    void setUp() throws IOException {
        executable = Files.createFile( directory.resolve( "server.exe" ) ).toRealPath();
        underTest = new ProcessIdentity( 42, CREATED_AT, executable );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that an operating-system creation instant is captured exactly, rather than replaced by a clock. */
    @Test
    void shouldCaptureIdentity_whenProcessMatches() {
        // GIVEN
        givenIdentity();

        // WHEN
        var result = ProcessIdentity.capture( handle, executable );

        // THEN
        assertThat( result ).isEqualTo( underTest );
    }

    /** Verifies that an unchanged live process passes the pre-signal identity check. */
    @Test
    void shouldVerifyIdentity_whenProcessStillMatches() {
        // GIVEN
        givenIdentity();

        // WHEN
        var thrown = catchThrowable( () -> underTest.verify( handle ) );

        // THEN
        assertThat( thrown ).isNull();
    }

    /** Verifies rejection of a PID that now belongs to a process created at a different instant. */
    @Test
    void shouldRejectProcess_whenPidWasReused() {
        // GIVEN
        givenIdentity();
        given( information.startInstant() ).willReturn( Optional.of( CREATED_AT.plusSeconds( 1 ) ) );

        // WHEN
        var thrown = catchThrowable( () -> underTest.verify( handle ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "identity" );
    }

    /** Verifies that a different PID is rejected before metadata or filesystem access. */
    @Test
    void shouldRejectProcess_whenPidDiffers() {
        // GIVEN
        given( handle.pid() ).willReturn( 43L );

        // WHEN
        var thrown = catchThrowable( () -> underTest.verify( handle ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
        verifyNoInteractions( information );
    }

    /** Verifies safe rejection when Windows does not expose a process creation time. */
    @Test
    void shouldRejectCapture_whenCreationTimeIsMissing() {
        // GIVEN
        given( handle.info() ).willReturn( information );

        // WHEN
        var thrown = catchThrowable( () -> ProcessIdentity.capture( handle, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "creation time" );
    }

    /** Verifies safe rejection when Windows does not expose an executable. */
    @Test
    void shouldRejectCapture_whenExecutableIsMissing() {
        // GIVEN
        given( handle.info() ).willReturn( information );
        given( information.startInstant() ).willReturn( Optional.of( CREATED_AT ) );

        // WHEN
        var thrown = catchThrowable( () -> ProcessIdentity.capture( handle, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "executable" );
    }

    /** Verifies that dead processes cannot be used as destinations even with matching metadata. */
    @Test
    void shouldRejectCapture_whenProcessIsDead() {
        // GIVEN
        givenMetadata();

        // WHEN
        var thrown = catchThrowable( () -> ProcessIdentity.capture( handle, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
    }

    /** Verifies rejection of a live process executing a different real file. */
    @Test
    void shouldRejectCapture_whenExecutableDiffers() throws IOException {
        // GIVEN
        givenMetadata();
        given( handle.isAlive() ).willReturn( true );
        Path other = Files.createFile( directory.resolve( "other.exe" ) );

        // WHEN
        var thrown = catchThrowable( () -> ProcessIdentity.capture( handle, other ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
    }

    /** Verifies that path I/O errors keep their cause rather than silently accepting an identity. */
    @Test
    void shouldKeepCause_whenExecutableCannotBeResolved() {
        // GIVEN
        given( handle.info() ).willReturn( information );
        given( information.startInstant() ).willReturn( Optional.of( CREATED_AT ) );
        given( information.command() ).willReturn( Optional.of( directory.resolve( "missing.exe" ).toString() ) );

        // WHEN
        var thrown = catchThrowable( () -> ProcessIdentity.capture( handle, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class ).hasCauseInstanceOf( IOException.class );
    }

    /** Verifies rejection of reserved or out-of-range Windows process identifiers.
     * @param pid
     *         invalid process identifier
     */
    @ParameterizedTest( name = "[{index}] invalid PID {0}" )
    @ValueSource( longs = { -1, 0, 4294967295L, 4294967296L } )
    void shouldRejectIdentity_whenPidIsInvalid( long pid ) {
        // GIVEN
        // Invalid PID from the parameter source.

        // WHEN
        var thrown = catchThrowable( () -> new ProcessIdentity( pid, CREATED_AT, executable ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies rejection of paths that are relative or still contain traversal segments.
     * @param path
     *         invalid executable path
     */
    @ParameterizedTest( name = "[{index}] invalid executable {0}" )
    @ValueSource( strings = { "server.exe", "C:/installation/../server.exe" } )
    void shouldRejectIdentity_whenPathIsInvalid( String path ) {
        // GIVEN
        Path input = Path.of( path );

        // WHEN
        var thrown = catchThrowable( () -> new ProcessIdentity( 42, CREATED_AT, input ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies all required reference inputs reject null before touching operating-system boundaries. */
    @Test
    void shouldRejectNull_whenRequiredInputIsMissing() {
        // GIVEN
        // Valid remaining arguments.

        // WHEN
        var timeFailure = catchThrowable( () -> new ProcessIdentity( 42, null, executable ) );
        var pathFailure = catchThrowable( () -> new ProcessIdentity( 42, CREATED_AT, null ) );
        var handleFailure = catchThrowable( () -> ProcessIdentity.capture( null, executable ) );
        var expectedFailure = catchThrowable( () -> ProcessIdentity.capture( handle, null ) );
        var verifyFailure = catchThrowable( () -> underTest.verify( null ) );

        // THEN
        assertThat( timeFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "startedAt" );
        assertThat( pathFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "executable" );
        assertThat( handleFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "handle" );
        assertThat( expectedFailure ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "expectedExecutable" );
        assertThat( verifyFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "handle" );
        verifyNoInteractions( handle, information );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenMetadata() {
        given( handle.info() ).willReturn( information );
        given( information.startInstant() ).willReturn( Optional.of( CREATED_AT ) );
        given( information.command() ).willReturn( Optional.of( executable.toString() ) );
    }

    private void givenIdentity() {
        givenMetadata();
        given( handle.isAlive() ).willReturn( true );
        given( handle.pid() ).willReturn( 42L );
    }
}
