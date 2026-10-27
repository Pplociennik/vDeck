package com.github.pplociennik.vdeck.feature.logs.service.impl;

import com.github.pplociennik.vdeck.feature.logs.dto.LogChunk;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies how {@link LogServiceImpl} reads the current launch's log from a real file in an isolated
 * directory, with the server service reporting which file belongs to the launch.
 */
@ExtendWith( MockitoExtension.class )
class LogServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String LOG_NAME = "2026-10-27_12-00-00.log";

    @TempDir
    private Path directory;

    @Mock
    private ServerService serverService;

    private Path logFile;

    private LogServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service and the path of the launch's log, which each test creates as it needs.
     */
    @BeforeEach
    void setUp() {
        logFile = directory.resolve( LOG_NAME );
        underTest = new LogServiceImpl( serverService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the service cannot be created without the server service it reads the launch from.
     */
    @Test
    void shouldThrowException_whenServerServiceIsNull() {
        // GIVEN
        // no server service

        // WHEN
        var exception = catchThrowable( () -> new LogServiceImpl( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "serverService" );
    }

    /**
     * Verifies that a panel without any launch log returns an empty chunk without a source, so the console
     * stays empty.
     */
    @Test
    void shouldReturnEmptyChunkWithoutSource_whenNoLaunchHasLog() {
        // GIVEN
        given( serverService.getStatus() ).willReturn( ServerStatus.stopped() );

        // WHEN
        LogChunk result = underTest.readCurrent( null );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( null, 0, "", true ) );
    }

    /**
     * Verifies that a launch whose server has not created its log yet yields an empty chunk naming the
     * expected log, instead of an error.
     */
    @Test
    void shouldReturnEmptyChunk_whenLogFileDoesNotExistYet() {
        // GIVEN
        givenRunningLaunch();

        // WHEN
        LogChunk result = underTest.readCurrent( null );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 0, "", true ) );
    }

    /**
     * Verifies that the first poll of a small log returns all of its complete lines and the position after
     * them, marked as a reset.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReturnWholeLog_whenFirstPollReadsSmallLog() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "first\nsecond\n" );

        // WHEN
        LogChunk result = underTest.readCurrent( null );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 13, "first\nsecond\n", true ) );
    }

    /**
     * Verifies that the first poll of a log larger than the tail returns only its end, without the line
     * the tail cuts at its start.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReturnTailWithoutCutFirstLine_whenFirstPollReadsLargeLog() throws IOException {
        // GIVEN
        givenRunningLaunch();
        String older = "x".repeat( LogServiceImpl.TAIL_BYTES ) + "\n";
        write( older + "cut start\nlast\n" );
        long size = Files.size( logFile );

        // WHEN
        LogChunk result = underTest.readCurrent( null );

        // THEN
        assertThat( result.text() ).isEqualTo( "cut start\nlast\n" );
        assertThat( result.offset() ).isEqualTo( size );
        assertThat( result.reset() ).isTrue();
    }

    /**
     * Verifies that a poll continuing from the previous position returns only the lines appended since.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReturnOnlyAppendedLines_whenPollContinuesFromPreviousOffset() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "first\nsecond\n" );

        // WHEN
        LogChunk result = underTest.readCurrent( 6L );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 13, "second\n", false ) );
    }

    /**
     * Verifies that a poll at the end of the log returns no text and keeps the position.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReturnNoText_whenNothingWasAppended() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "first\n" );

        // WHEN
        LogChunk result = underTest.readCurrent( 6L );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 6, "", false ) );
    }

    /**
     * Verifies that a line the server is still writing is held back until its line break arrives.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldHoldBackIncompleteLine_whenLogEndsWithoutLineBreak() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "done\nstill writ" );

        // WHEN
        LogChunk result = underTest.readCurrent( 0L );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 5, "done\n", false ) );
    }

    /**
     * Verifies that a position the file does not reach, or a negative one, starts the console over from
     * the end of the log.
     *
     * @param offset
     *         position the log cannot continue from
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @ParameterizedTest( name = "[{index}] offset={0} should start over" )
    @ValueSource( longs = { -1, 100 } )
    void shouldStartOverFromTail_whenOffsetIsOutsideLog( long offset ) throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "first\n" );

        // WHEN
        LogChunk result = underTest.readCurrent( offset );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 6, "first\n", true ) );
    }

    /**
     * Verifies that a console far behind the log catches up over several polls, each returning at most
     * one chunk of complete lines.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldLimitChunkSize_whenConsoleIsFarBehind() throws IOException {
        // GIVEN
        givenRunningLaunch();
        String line = "y".repeat( 1023 ) + "\n";
        write( line.repeat( 300 ) );

        // WHEN
        LogChunk result = underTest.readCurrent( 0L );

        // THEN
        assertThat( result.offset() ).isEqualTo( LogServiceImpl.MAX_CHUNK_BYTES );
        assertThat( result.text() ).isEqualTo( line.repeat( 256 ) );
    }

    /**
     * Verifies that a full chunk without any line break is returned whole, so a single huge line cannot
     * stall the console.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReturnWholeBlock_whenFullChunkHasNoLineBreak() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "z".repeat( LogServiceImpl.MAX_CHUNK_BYTES + 10 ) );

        // WHEN
        LogChunk result = underTest.readCurrent( 0L );

        // THEN
        assertThat( result.offset() ).isEqualTo( LogServiceImpl.MAX_CHUNK_BYTES );
        assertThat( result.text() ).hasSize( LogServiceImpl.MAX_CHUNK_BYTES );
    }

    /**
     * Verifies that Polish characters are decoded as UTF-8 and Windows line endings become single line
     * breaks, while the position still counts the bytes of the file.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldDecodeUtf8AndDropCarriageReturns_whenLogUsesWindowsLineEndings() throws IOException {
        // GIVEN
        givenRunningLaunch();
        write( "Zażółć gęślą jaźń\r\n" );
        long size = Files.size( logFile );

        // WHEN
        LogChunk result = underTest.readCurrent( 0L );

        // THEN
        assertThat( result ).isEqualTo(
                new LogChunk( LOG_NAME, size, "Zażółć gęślą jaźń\n", false ) );
    }

    /**
     * Verifies that a crashed launch keeps showing its log, which tells the administrator why it ended.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldReadLogOfCrashedLaunch_whenServerCrashed() throws IOException {
        // GIVEN
        given( serverService.getStatus() ).willReturn( ServerStatus.crashed( logFile ) );
        write( "fatal\n" );

        // WHEN
        LogChunk result = underTest.readCurrent( null );

        // THEN
        assertThat( result ).isEqualTo( new LogChunk( LOG_NAME, 6, "fatal\n", true ) );
    }

    /**
     * Verifies that a log path that cannot be read as a file fails with an unchecked I/O error rather than
     * an empty console.
     *
     * @throws IOException
     *         if the test log cannot be prepared
     */
    @Test
    void shouldThrowUncheckedIoException_whenLogCannotBeRead() throws IOException {
        // GIVEN
        givenRunningLaunch();
        Files.createDirectory( logFile );

        // WHEN
        var exception = catchThrowable( () -> underTest.readCurrent( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( UncheckedIOException.class ).hasMessageContaining( LOG_NAME );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenRunningLaunch() {
        given( serverService.getStatus() ).willReturn( ServerStatus.running( 42L, Instant.EPOCH, logFile ) );
    }

    private void write( String content ) throws IOException {
        Files.writeString( logFile, content, StandardCharsets.UTF_8 );
    }
}
