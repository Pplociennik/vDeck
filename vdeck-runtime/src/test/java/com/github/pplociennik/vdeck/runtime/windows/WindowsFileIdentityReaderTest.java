package com.github.pplociennik.vdeck.runtime.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/** Verifies fail-closed Windows file identity reads and ownership of every native handle. */
@ExtendWith( MockitoExtension.class )
class WindowsFileIdentityReaderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    @Mock
    private Win32 nativeCalls;

    private WindowsFileIdentityReader underTest;

    private final MemorySegment handle = MemorySegment.ofAddress( 42 );

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a reader without performing any native filesystem calls. */
    @BeforeEach
    void setUp() {
        underTest = new WindowsFileIdentityReader( nativeCalls );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** The complete volume serial and 128-bit ID are returned, then the owned handle is closed. */
    @Test
    void shouldReadCompleteIdentity_whenNativeCallSucceeds() throws Exception {
        // GIVEN
        byte[] bytes = HexFormat.of().parseHex( "0102030405060708090a0b0c0d0e0f101112131415161718" );
        given( nativeCalls.openFileForIdentity( any() ) ).willReturn( handle );
        given( nativeCalls.readFileIdentity( eq( handle ), any() ) ).willAnswer( invocation -> {
            MemorySegment output = invocation.getArgument( 1 );
            output.copyFrom( MemorySegment.ofArray( bytes ) );
            return 1;
        } );
        given( nativeCalls.closeHandle( handle ) ).willReturn( 1 );
        // WHEN
        var result = underTest.identify( directory.resolve( "world.db" ) );
        // THEN
        assertThat( result ).isEqualTo( HexFormat.of().formatHex( bytes ) );
        verify( nativeCalls ).closeHandle( handle );
    }

    /** A failed open has no handle to close and never produces an identity. */
    @Test
    void shouldFailClosed_whenNativeOpenFails() {
        // GIVEN
        given( nativeCalls.openFileForIdentity( any() ) ).willReturn( MemorySegment.ofAddress( -1 ) );
        given( nativeCalls.getLastError() ).willReturn( 5 );
        // WHEN
        var failure = catchThrowable( () -> underTest.identify( directory.resolve( "world.db" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "CreateFileW" );
        verify( nativeCalls, never() ).closeHandle( any() );
        verify( nativeCalls, never() ).readFileIdentity( any(), any() );
    }

    /** Both native read failure and cleanup failure remain observable without losing the first error. */
    @Test
    void shouldPreserveReadFailure_whenClosingHandleAlsoFails() {
        // GIVEN
        given( nativeCalls.openFileForIdentity( any() ) ).willReturn( handle );
        // WHEN
        var failure = catchThrowable( () -> underTest.identify( directory.resolve( "world.db" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "GetFileInformationByHandleEx" );
        assertThat( failure.getSuppressed() ).hasSize( 1 );
        assertThat( failure.getSuppressed()[0] ).hasMessageContaining( "CloseHandle" );
        verify( nativeCalls ).closeHandle( handle );
    }

    /** Required inputs are validated before any native resource is opened. */
    @Test
    void shouldRejectMissingInput_whenPathOrBoundaryIsNull() {
        // GIVEN
        // The fixture has no native stubbing.
        // WHEN
        var pathFailure = catchThrowable( () -> underTest.identify( null ) );
        var boundaryFailure = catchThrowable( () -> new WindowsFileIdentityReader( null ) );
        var relativeFailure = catchThrowable( () -> underTest.identify( Path.of( "world.db" ) ) );
        // THEN
        assertThat( pathFailure ).isInstanceOf( NullPointerException.class );
        assertThat( boundaryFailure ).isInstanceOf( NullPointerException.class );
        assertThat( relativeFailure ).isInstanceOf( IOException.class );
        verifyNoInteractions( nativeCalls );
    }

    /** Native IDs stay stable across writes but change on atomic replacement despite equal metadata. */
    @Test
    @EnabledOnOs( OS.WINDOWS )
    void shouldDetectReplacement_whenWindowsFileMetadataIsUnchanged() throws Exception {
        // GIVEN
        underTest = new WindowsFileIdentityReader();
        Path file = Files.writeString( directory.resolve( "Valheim Serwer \u0105\u0119.db" ), "first" );
        String original = underTest.identify( file );
        var modified = Files.getLastModifiedTime( file );
        Files.writeString( file, "other" );
        String afterWrite = underTest.identify( file );
        Path replacement = Files.writeString( directory.resolve( "replacement.db" ), "third" );
        Files.setLastModifiedTime( replacement, modified );
        // WHEN
        Files.move( replacement, file, ATOMIC_MOVE, REPLACE_EXISTING );
        String afterReplacement = underTest.identify( file );
        // THEN
        assertThat( original ).hasSize( 48 ).isEqualTo( afterWrite ).isNotEqualTo( afterReplacement );
        assertThat( Files.getLastModifiedTime( file ) ).isEqualTo( modified );
        assertThat( Files.size( file ) ).isEqualTo( 5 );
    }
}
