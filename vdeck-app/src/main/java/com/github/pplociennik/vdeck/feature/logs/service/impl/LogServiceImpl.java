package com.github.pplociennik.vdeck.feature.logs.service.impl;

import com.github.pplociennik.vdeck.feature.logs.dto.LogChunk;
import com.github.pplociennik.vdeck.feature.logs.service.LogService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * Reads the current launch's game server log straight from the file the server writes, without keeping
 * any of it in memory between polls.
 *
 * <p>Every chunk ends at the last line break it contains, so a line the server is still writing is
 * returned by a later poll as a whole and a multi-byte character is never split between two chunks. Only
 * a block filled to {@link #MAX_CHUNK_BYTES} without any line break is returned as it is, so a single
 * huge line cannot stall the console.
 *
 * <p>Java opens the file with read and write sharing on Windows, so reading never blocks the server
 * appending to it.
 */
@Log4j2
@Service
class LogServiceImpl implements LogService {

    /**
     * Size of the log end shown when the console opens or starts over.
     */
    static final int TAIL_BYTES = 256 * 1024;

    /**
     * Largest part of the log returned to one poll; a console that fell behind catches up over several.
     */
    static final int MAX_CHUNK_BYTES = 256 * 1024;

    private static final byte LINE_FEED = '\n';

    private final ServerService serverService;

    /**
     * Creates the service reading the log of the launch the server service reports.
     *
     * @param serverService
     *         service reporting the current launch and its log file
     * @throws NullPointerException
     *         if the service is null
     */
    LogServiceImpl( @Nonnull ServerService serverService ) {
        this.serverService = requireNonNull( serverService, "serverService must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull LogChunk readCurrent( @Nullable Long offset ) {
        Path logFile = serverService.getStatus().getLogFile();
        if ( logFile == null ) {
            return LogChunk.none();
        }

        String source = logFile.getFileName().toString();
        try ( FileChannel channel = FileChannel.open( logFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS ) ) {
            long size = channel.size();
            if ( offset == null || offset < 0 || offset > size ) {
                log.debug( "Console starts over at the end of the game server log {}", source );
                return readTail( channel, size, source );
            }
            return readFrom( channel, offset, size, source );
        } catch ( NoSuchFileException exception ) {
            // The server creates its log shortly after the process starts; until then there is nothing to show.
            log.trace( "Game server log {} does not exist yet", source );
            return new LogChunk( source, 0, "", true );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not read the game server log " + source, exception );
        }
    }

    /**
     * Returns the end of the log. The first line of a tail that does not start at the beginning of the file
     * is most likely cut, so it is dropped.
     */
    private static @Nonnull LogChunk readTail( @Nonnull FileChannel channel, long size, @Nonnull String source )
            throws IOException {
        long start = Math.max( 0, size - TAIL_BYTES );
        byte[] bytes = read( channel, start, (int) ( size - start ) );

        int skip = start > 0 ? firstLineLength( bytes ) : 0;
        int end = completeLength( bytes, skip );
        return new LogChunk( source, start + end, decode( bytes, skip, end ), true );
    }

    private static @Nonnull LogChunk readFrom( @Nonnull FileChannel channel, long offset, long size,
                                               @Nonnull String source ) throws IOException {
        byte[] bytes = read( channel, offset, (int) Math.min( size - offset, MAX_CHUNK_BYTES ) );

        int end = completeLength( bytes, 0 );
        return new LogChunk( source, offset + end, decode( bytes, 0, end ), false );
    }

    private static @Nonnull byte[] read( @Nonnull FileChannel channel, long position, int length ) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate( length );
        while ( buffer.hasRemaining() && channel.read( buffer, position + buffer.position() ) >= 0 ) {
            // a positional read may return fewer bytes than asked for, so it repeats until the block is full
        }
        return Arrays.copyOf( buffer.array(), buffer.position() );
    }

    /**
     * Returns the length of the first line including its line break, or 0 when the block holds no line
     * break at all and so is kept whole.
     */
    private static int firstLineLength( @Nonnull byte[] bytes ) {
        for ( int index = 0; index < bytes.length; index++ ) {
            if ( bytes[ index ] == LINE_FEED ) {
                return index + 1;
            }
        }
        return 0;
    }

    /**
     * Returns where the complete lines of the block end. A block without a line break after {@code skip} is
     * either a line still being written, which waits for the next poll, or a full block, which is returned
     * whole.
     */
    private static int completeLength( @Nonnull byte[] bytes, int skip ) {
        for ( int index = bytes.length - 1; index >= skip; index-- ) {
            if ( bytes[ index ] == LINE_FEED ) {
                return index + 1;
            }
        }
        return bytes.length == MAX_CHUNK_BYTES ? bytes.length : skip;
    }

    /**
     * Decodes the lines as UTF-8, replacing malformed bytes, and drops carriage returns so Windows line
     * endings render as single line breaks.
     */
    private static @Nonnull String decode( @Nonnull byte[] bytes, int from, int to ) {
        return new String( bytes, from, to - from, StandardCharsets.UTF_8 ).replace( "\r", "" );
    }
}
