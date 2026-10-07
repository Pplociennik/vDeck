package com.github.pplociennik.vdeck.runtime.windows;

import com.github.pplociennik.vdeck.runtime.spi.FileIdentityReader;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;

import static java.util.Objects.requireNonNull;

/** Reads Windows volume and 128-bit file IDs without locking Valheim out of writes or renames. */
@Log4j2
public final class WindowsFileIdentityReader implements FileIdentityReader {

    private final Win32 nativeCalls;

    /** Creates the Windows filesystem boundary. */
    public WindowsFileIdentityReader() {
        this( new Win32() );
    }

    /**
     * Creates a reader with a controlled native boundary.
     *
     * @param nativeCalls
     *         Windows API adapter
     * @throws NullPointerException
     *         if the adapter is null
     */
    WindowsFileIdentityReader( @Nonnull Win32 nativeCalls ) {
        this.nativeCalls = requireNonNull( nativeCalls, "nativeCalls must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull String identify( @Nonnull Path file ) throws IOException {
        requireNonNull( file, "file must not be null" );
        if ( !file.isAbsolute() ) {
            throw new IOException( "File identity requires an absolute path" );
        }
        try ( Arena arena = Arena.ofConfined() ) {
            String path = file.toString();
            String extended = path.startsWith( "\\\\?\\" ) ? path
                    : path.startsWith( "\\\\" ) ? "\\\\?\\UNC\\" + path.substring( 2 ) : "\\\\?\\" + path;
            byte[] encoded = ( extended + '\0' ).getBytes( StandardCharsets.UTF_16LE );
            MemorySegment name = arena.allocate( encoded.length, 2 );
            name.copyFrom( MemorySegment.ofArray( encoded ) );
            MemorySegment handle = nativeCalls.openFileForIdentity( name );
            if ( handle.address() == -1L || handle.address() == 0 ) {
                throw failure( "CreateFileW" );
            }
            Throwable problem = null;
            try {
                MemorySegment information = arena.allocate( 24, 8 );
                if ( nativeCalls.readFileIdentity( handle, information ) == 0 ) {
                    throw failure( "GetFileInformationByHandleEx" );
                }
                log.debug( "Read Windows volume and file identity" );
                return HexFormat.of().formatHex( information.toArray( ValueLayout.JAVA_BYTE ) );
            } catch ( IOException | RuntimeException exception ) {
                problem = exception;
                throw exception;
            } finally {
                try {
                    if ( nativeCalls.closeHandle( handle ) == 0 ) {
                        throw failure( "CloseHandle" );
                    }
                } catch ( IOException | RuntimeException cleanupFailure ) {
                    if ( problem == null ) {
                        throw cleanupFailure;
                    }
                    problem.addSuppressed( cleanupFailure );
                }
            }
        }
    }

    private IOException failure( String operation ) {
        return new IOException( "%s failed with Windows error %d".formatted( operation, nativeCalls.getLastError() ) );
    }
}
