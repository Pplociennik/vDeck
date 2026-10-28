package com.github.pplociennik.vdeck.feature.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Verifies the layout of the game server logs and the retention applied before a launch.
 *
 * <p>A fixed clock makes same-second collisions and retention boundaries deterministic.
 */
@ExtendWith( MockitoExtension.class )
class ServerLogServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Clock CLOCK = Clock.fixed( Instant.parse( "2026-10-06T23:59:59Z" ), ZoneOffset.UTC );

    private static final String CONFIGURED_DIRECTORY = ".vDeck/log";

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );

    private static final String DAY_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    private static final String FILE_PATTERN = DAY_PATTERN + "_\\d{2}-\\d{2}-\\d{2}\\.log";

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private PathService pathService;

    @TempDir
    private Path installationDirectory;

    private Path logDirectory;

    private ServerLogServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the service and points the configured log directory inside the isolated temporary
     * installation directory.
     */
    @BeforeEach
    void setUp() {
        logDirectory = installationDirectory.resolve( ".vDeck" ).resolve( "log" );
        underTest = new ServerLogServiceImpl( systemPropertiesService, pathService, CLOCK );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a launch gets its own file inside a directory named after the day, and that the two
     * names agree — the file name begins with exactly the day its directory is named after.
     */
    @Test
    void shouldPlaceLogInDayDirectory_whenLaunchIsPrepared() {
        // GIVEN
        givenConfiguredLogDirectory();

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        String dayDirectory = result.getParent().getFileName().toString();
        String fileName = result.getFileName().toString();
        assertThat( dayDirectory ).matches( DAY_PATTERN );
        assertThat( fileName ).matches( FILE_PATTERN ).startsWith( dayDirectory + "_" );
        assertThat( result.getParent().getParent() ).isEqualTo( logDirectory );
    }

    /**
     * Verifies that the day directory is created, since the server will not create it itself.
     */
    @Test
    void shouldCreateDayDirectory_whenItDoesNotExist() {
        // GIVEN
        givenConfiguredLogDirectory();

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result.getParent() ).isDirectory();
    }

    /**
     * Verifies that the log file itself is only reserved, not created: the server owns the file and
     * creates it when it starts.
     */
    @Test
    void shouldNotCreateLogFile_whenOnlyReservingIt() {
        // GIVEN
        givenConfiguredLogDirectory();

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result ).doesNotExist();
    }

    /**
     * Verifies that a name already taken is never handed out again, so a second launch cannot overwrite
     * the log of the first.
     *
     * @throws IOException
     *         if the occupying file cannot be created
     */
    @Test
    void shouldReturnUnusedName_whenPreviousLogAlreadyOccupiesIt() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        Path firstLog = underTest.createLogFileForNewStart();
        Files.createFile( firstLog );

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result ).isNotEqualTo( firstLog ).doesNotExist();
        assertThat( result.getFileName().toString() ).isEqualTo( "2026-10-06_23-59-59-1.log" );
        assertThat( firstLog ).hasContent( "" );
    }

    /**
     * Verifies that a day directory older than the retention window is removed together with the logs
     * inside it.
     *
     * @throws IOException
     *         if the expired directory cannot be prepared
     */
    @Test
    void shouldRemoveExpiredDays_whenRetentionIsApplied() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "14" );
        Path expired = givenDayDirectoryAgedDays( 30 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( expired ).doesNotExist();
    }

    /**
     * Verifies that a day inside the retention window survives, including the day exactly on the
     * boundary, so the window is not silently one day shorter than configured.
     *
     * @throws IOException
     *         if the day directories cannot be prepared
     */
    @Test
    void shouldKeepDaysInsideWindow_whenRetentionIsApplied() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "14" );
        Path recent = givenDayDirectoryAgedDays( 2 );
        Path onBoundary = givenDayDirectoryAgedDays( 14 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( recent ).isDirectory();
        assertThat( onBoundary ).isDirectory();
    }

    /**
     * Verifies that a directory whose name is not a date is left alone, because it belongs to someone
     * other than the retention policy.
     *
     * @throws IOException
     *         if the directory cannot be prepared
     */
    @Test
    void shouldIgnoreForeignDirectories_whenRetentionIsApplied() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "14" );
        Path foreign = Files.createDirectories( logDirectory.resolve( "archive" ) );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( foreign ).isDirectory();
    }

    /**
     * Verifies that a launch still gets its log file when the configured retention value is not a
     * number, because a broken setting must not stop the server from starting.
     *
     * @throws IOException
     *         if the expired directory cannot be prepared
     */
    @Test
    void shouldFallBackToDefaultRetention_whenConfiguredValueIsNotANumber() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "not-a-number" );
        Path expired = givenDayDirectoryAgedDays( 60 );

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result.getFileName().toString() ).matches( FILE_PATTERN );
        assertThat( expired ).doesNotExist();
    }

    /**
     * Verifies that a log directory that cannot be created is reported, so the caller can refuse the
     * launch instead of letting the server start with nowhere to write.
     *
     * @throws IOException
     *         if the blocking file cannot be created
     */
    @Test
    void shouldThrowException_whenLogDirectoryCannotBeCreated() throws IOException {
        // GIVEN
        Path blocked = installationDirectory.resolve( "blocked" );
        Files.createFile( blocked );
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.GAME_LOG_DIRECTORY ) )
                .willReturn( "blocked" );
        given( pathService.getAbsolutePath( "blocked" ) ).willReturn( blocked );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.createLogFileForNewStart() );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to create game log directory" );
    }

    /**
     * Verifies that the service cannot be created without the settings it reads.
     */
    @Test
    void shouldThrowException_whenSystemPropertiesServiceIsNull() {
        // GIVEN
        // no settings service

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerLogServiceImpl( null, pathService ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService must not be null" );
    }

    /**
     * Verifies that the service cannot be created without the path resolution that keeps the directory
     * inside the installation.
     */
    @Test
    void shouldThrowException_whenPathServiceIsNull() {
        // GIVEN
        // no path service

        // WHEN
        Throwable thrown = catchThrowable( () -> new ServerLogServiceImpl( systemPropertiesService, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "pathService must not be null" );
    }

    /**
     * Verifies repeated collisions use the first available suffix and preserve earlier logs.
     *
     * @throws IOException
     *         if the occupied files cannot be prepared
     */
    @Test
    void shouldUseSecondSuffix_whenBaseAndFirstSuffixAreOccupied() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        Path first = underTest.createLogFileForNewStart();
        Files.writeString( first, "first launch" );
        Path second = first.resolveSibling( "2026-10-06_23-59-59-1.log" );
        Files.writeString( second, "second launch" );

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result ).isEqualTo( first.resolveSibling( "2026-10-06_23-59-59-2.log" ) ).doesNotExist();
        assertThat( first ).hasContent( "first launch" );
        assertThat( second ).hasContent( "second launch" );
    }

    /**
     * Verifies that zero retention days keep every log, however old, while retention stays switched on.
     *
     * @throws IOException
     *         if the day directories cannot be prepared
     */
    @Test
    void shouldKeepEveryLog_whenRetentionIsZero() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "0" );
        Path yesterday = givenDayDirectoryAgedDays( 1 );
        Path ancient = givenDayDirectoryAgedDays( 400 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( yesterday.resolve( "old-run.log" ) ).exists();
        assertThat( ancient.resolve( "old-run.log" ) ).exists();
    }

    /**
     * Verifies that invalid negative retention falls back to fourteen days instead of deleting today's logs.
     *
     * @throws IOException
     *         if the day directories cannot be prepared
     */
    @Test
    void shouldUseDefaultWindow_whenRetentionIsNegative() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "-1" );
        Path today = givenDayDirectoryAgedDays( 0 );
        Path boundary = givenDayDirectoryAgedDays( 14 );
        Path expired = givenDayDirectoryAgedDays( 15 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( today.resolve( "old-run.log" ) ).exists();
        assertThat( boundary ).exists();
        assertThat( expired ).doesNotExist();
    }

    /**
     * Verifies a Windows read-only expired file does not prevent creating the next log directory.
     *
     * @throws IOException
     *         if the isolated file attributes cannot be set or restored
     */
    @Test
    @EnabledOnOs( OS.WINDOWS )
    void shouldPrepareNewLog_whenExpiredFileCannotBeDeleted() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "14" );
        Path expired = givenDayDirectoryAgedDays( 30 ).resolve( "old-run.log" );
        Files.setAttribute( expired, "dos:readonly", true );
        try {
            // WHEN
            Path result = underTest.createLogFileForNewStart();

            // THEN
            assertThat( result.getParent() ).isDirectory();
            assertThat( expired ).exists();
        } finally {
            Files.setAttribute( expired, "dos:readonly", false );
        }
    }

    /**
     * Verifies that switching retention off keeps every log, however old, and that the retention window
     * is then not even read.
     *
     * @throws IOException
     *         if the old day directory cannot be prepared
     */
    @Test
    void shouldKeepEveryLog_whenRetentionIsDisabled() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionEnabled( "false" );
        Path old = givenDayDirectoryAgedDays( 365 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( old.resolve( "old-run.log" ) ).exists();
        verify( systemPropertiesService, never() )
                .getValue( VdeckProperty.FILE_NAME, VdeckProperty.GAME_LOG_RETENTION_DAYS );
    }

    /**
     * Verifies that a retention switch which is neither {@code true} nor {@code false} falls back to its
     * default, so old logs are still removed.
     *
     * @throws IOException
     *         if the expired directory cannot be prepared
     */
    @Test
    void shouldApplyRetention_whenRetentionSwitchIsNotBoolean() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionEnabled( "maybe" );
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.GAME_LOG_RETENTION_DAYS ) )
                .willReturn( "14" );
        Path expired = givenDayDirectoryAgedDays( 30 );

        // WHEN
        underTest.createLogFileForNewStart();

        // THEN
        assertThat( expired ).doesNotExist();
    }

    /**
     * Verifies that an existing directory of the current day is reused as it is, keeping the logs of the
     * earlier launches of that day.
     *
     * @throws IOException
     *         if the day directory cannot be prepared
     */
    @Test
    void shouldReuseDayDirectory_whenItAlreadyExists() throws IOException {
        // GIVEN
        givenConfiguredLogDirectory();
        givenRetentionDays( "14" );
        Path today = givenDayDirectoryAgedDays( 0 );

        // WHEN
        Path result = underTest.createLogFileForNewStart();

        // THEN
        assertThat( result.getParent() ).isEqualTo( today );
        assertThat( today.resolve( "old-run.log" ) ).exists();
    }

    /** Verifies that date generation cannot be configured with an absent clock. */
    @Test
    void shouldRejectConstruction_whenClockIsNull() {
        // GIVEN
        // No clock.

        // WHEN
        var thrown = catchThrowable( () -> new ServerLogServiceImpl( systemPropertiesService, pathService, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "clock" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Points the configured log directory at the temporary installation directory.
     */
    private void givenConfiguredLogDirectory() {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.GAME_LOG_DIRECTORY ) )
                .willReturn( CONFIGURED_DIRECTORY );
        given( pathService.getAbsolutePath( CONFIGURED_DIRECTORY ) ).willReturn( logDirectory );
    }

    /**
     * Switches retention on and configures how many days of logs are kept. Only needed by tests whose
     * log directory already exists, because retention is skipped entirely on a first launch.
     *
     * @param value
     *         raw configured value, which may deliberately be unparsable
     */
    private void givenRetentionDays( String value ) {
        givenRetentionEnabled( "true" );
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_DAYS ) ).willReturn( value );
    }

    /**
     * Configures whether old logs are removed at all.
     *
     * @param value
     *         raw configured value, which may deliberately be neither {@code true} nor {@code false}
     */
    private void givenRetentionEnabled( String value ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_ENABLED ) ).willReturn( value );
    }

    /**
     * Creates a day directory aged a given number of days, holding one log file, so retention has
     * something realistic to act on.
     *
     * @param age
     *         how many days before today the directory is dated
     * @return the created day directory
     * @throws IOException
     *         if it cannot be created
     */
    private Path givenDayDirectoryAgedDays( int age ) throws IOException {
        Path day = logDirectory.resolve( DAY_FORMAT.format( LocalDate.now( CLOCK ).minusDays( age ) ) );
        Files.createDirectories( day );
        Files.createFile( day.resolve( "old-run.log" ) );
        return day;
    }
}
