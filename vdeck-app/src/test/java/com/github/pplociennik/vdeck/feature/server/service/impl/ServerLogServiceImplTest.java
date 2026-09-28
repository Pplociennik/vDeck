package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies the layout of the game server logs and the retention applied before a launch.
 *
 * <p>No assertion names a concrete date. The naming tests check the structural relationship between a
 * day directory and the file inside it, which cannot break at midnight, and the retention tests build
 * their own day directories relative to the current date instead of hard-coding one.
 */
@ExtendWith( MockitoExtension.class )
class ServerLogServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

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
        underTest = new ServerLogServiceImpl( systemPropertiesService, pathService );
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
        assertThat( result.getFileName().toString() ).endsWith( ".log" );
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
     * Configures how many days of logs are kept. Only needed by tests whose log directory already
     * exists, because retention is skipped entirely on a first launch.
     *
     * @param value
     *         raw configured value, which may deliberately be unparsable
     */
    private void givenRetentionDays( String value ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME,
                VdeckProperty.GAME_LOG_RETENTION_DAYS ) ).willReturn( value );
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
        Path day = logDirectory.resolve( DAY_FORMAT.format( LocalDate.now().minusDays( age ) ) );
        Files.createDirectories( day );
        Files.createFile( day.resolve( "old-run.log" ) );
        return day;
    }
}