package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link PanelLogRetention} removes only the panel run logs outside the configured window.
 */
@ExtendWith( MockitoExtension.class )
class PanelLogRetentionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Clock CLOCK = Clock.fixed( Instant.parse( "2026-10-28T12:00:00Z" ), ZoneOffset.UTC );

    @TempDir
    private Path directory;

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private PathService pathService;

    private Path logs;

    private PanelLogRetention underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the panel log directory of a canonical installation and the retention for 28 October 2026.
     */
    @BeforeEach
    void setUp() throws Exception {
        Path installation = directory.toRealPath();
        logs = Files.createDirectories( installation.resolve( ".vDeck/logs/panel" ) );
        underTest = new PanelLogRetention( systemPropertiesService, pathService, CLOCK );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that days before the window are removed while the oldest kept day and today survive.
     */
    @Test
    void shouldRemoveDaysBeforeWindow_whenRetentionIsOn() throws Exception {
        // GIVEN
        givenSettings( "true", "2" );
        given( pathService.getBaseDirectory() ).willReturn( directory.toRealPath() );
        Path expired = day( "2026-10-25" );
        Path oldestKept = day( "2026-10-26" );
        Path today = day( "2026-10-28" );

        // WHEN
        underTest.apply();

        // THEN
        assertThat( expired ).doesNotExist();
        assertThat( oldestKept ).exists();
        assertThat( today ).exists();
    }

    /**
     * Verifies that zero days keep every panel log.
     */
    @Test
    void shouldKeepEveryLog_whenRetentionIsZeroDays() throws Exception {
        // GIVEN
        givenSettings( "true", "0" );
        Path ancient = day( "2024-01-01" );

        // WHEN
        underTest.apply();

        // THEN
        assertThat( ancient ).exists();
    }

    /**
     * Verifies that a switched-off retention keeps every panel log.
     */
    @Test
    void shouldKeepEveryLog_whenRetentionIsSwitchedOff() throws Exception {
        // GIVEN
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_LOG_RETENTION_ENABLED ) )
                .willReturn( "false" );
        Path ancient = day( "2024-01-01" );

        // WHEN
        underTest.apply();

        // THEN
        assertThat( ancient ).exists();
    }

    /**
     * Verifies that missing dependencies are rejected when the retention is created.
     */
    @Test
    void shouldRejectNull_whenDependencyIsMissing() {
        // GIVEN
        // no dependencies

        // WHEN
        var missingService = catchThrowable( () -> new PanelLogRetention( null, pathService, CLOCK ) );
        var missingPaths = catchThrowable( () -> new PanelLogRetention( systemPropertiesService, null, CLOCK ) );
        var missingClock = catchThrowable( () -> new PanelLogRetention( systemPropertiesService, pathService, null ) );

        // THEN
        assertThat( missingService ).isInstanceOf( NullPointerException.class );
        assertThat( missingPaths ).isInstanceOf( NullPointerException.class );
        assertThat( missingClock ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenSettings( String enabled, String days ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_LOG_RETENTION_ENABLED ) )
                .willReturn( enabled );
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_LOG_RETENTION_DAYS ) )
                .willReturn( days );
    }

    private Path day( String name ) throws Exception {
        Path day = Files.createDirectory( logs.resolve( name ) );
        Files.writeString( day.resolve( "run.log" ), "entry" );
        return day;
    }
}
