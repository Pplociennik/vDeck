package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies MVC behavior of {@link ServerStateController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class ServerStateControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Instant NOW = Instant.parse( "2026-10-29T12:00:00Z" );

    private static final ZoneOffset ZONE = ZoneOffset.ofHours( 2 );

    private static final Path LOG_FILE = Path.of( "C:/vdeck/.vDeck/logs/game/run.log" ).toAbsolutePath();

    @Mock
    private LaunchProfileService launchProfileService;

    private ServerStateController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated profile service and a fixed clock.
     */
    @BeforeEach
    void setUp() {
        underTest = new ServerStateController( launchProfileService, Clock.fixed( NOW, ZONE ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the controller cannot be created without the profile service.
     */
    @Test
    void shouldThrowException_whenLaunchProfileServiceIsNull() {
        // GIVEN
        // no profile service

        // WHEN
        var exception = catchThrowable( () -> new ServerStateController( null, Clock.systemUTC() ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "launchProfileService" );
    }

    /**
     * Verifies that the controller cannot be created without a clock.
     */
    @Test
    void shouldThrowException_whenClockIsNull() {
        // GIVEN
        // no clock

        // WHEN
        var exception = catchThrowable( () -> new ServerStateController( launchProfileService, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "clock" );
    }

    /**
     * Verifies that a stopped server shows the profile without any process times.
     */
    @Test
    void shouldAddProfileWithoutProcessTimes_whenServerIsStopped() {
        // GIVEN
        LaunchProfile profile = profile();
        given( launchProfileService.getProfile() ).willReturn( profile );
        var model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showServerState( ServerStatus.stopped(), model );

        // THEN
        assertThat( result ).isEqualTo( "server/server-state" );
        assertThat( model )
                .containsEntry( ServerStateController.LAUNCH_PROFILE_ATTRIBUTE, profile )
                .doesNotContainKeys( ServerStateController.STARTED_AT_ATTRIBUTE,
                        ServerStateController.UPTIME_ATTRIBUTE, ServerStateController.LOG_FILE_NAME_ATTRIBUTE );
    }

    /**
     * Verifies that a crashed launch keeps showing the name of its log, but no process times.
     */
    @Test
    void shouldAddLogFileNameOnly_whenServerCrashed() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( profile() );
        var model = new ExtendedModelMap();

        // WHEN
        underTest.showServerState( ServerStatus.crashed( LOG_FILE ), model );

        // THEN
        assertThat( model )
                .containsEntry( ServerStateController.LOG_FILE_NAME_ATTRIBUTE, "run.log" )
                .doesNotContainKeys( ServerStateController.STARTED_AT_ATTRIBUTE,
                        ServerStateController.UPTIME_ATTRIBUTE );
    }

    /**
     * Verifies that a managed process shows its creation time in the clock's zone and how long it has run.
     */
    @Test
    void shouldAddLocalStartTimeUptimeAndLogName_whenProcessIsManaged() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( profile() );
        Instant startedAt = NOW.minus( Duration.ofDays( 1 ).plusHours( 2 ).plusMinutes( 3 ) );
        var model = new ExtendedModelMap();

        // WHEN
        underTest.showServerState( ServerStatus.running( 42, startedAt, LOG_FILE ), model );

        // THEN
        assertThat( model )
                .containsEntry( ServerStateController.STARTED_AT_ATTRIBUTE, LocalDateTime.of( 2026, 10, 28, 11, 57 ) )
                .containsEntry( ServerStateController.UPTIME_ATTRIBUTE, Duration.ofMinutes( 24 * 60 + 2 * 60 + 3 ) )
                .containsEntry( ServerStateController.LOG_FILE_NAME_ATTRIBUTE, "run.log" );
    }

    /**
     * Verifies that a creation time ahead of the clock, as after the clock was set back, gives zero uptime.
     */
    @Test
    void shouldAddZeroUptime_whenStartIsAheadOfClock() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( profile() );
        var model = new ExtendedModelMap();

        // WHEN
        underTest.showServerState( ServerStatus.running( 42, NOW.plusSeconds( 30 ), LOG_FILE ), model );

        // THEN
        assertThat( model ).containsEntry( ServerStateController.UPTIME_ATTRIBUTE, Duration.ZERO );
    }

    /**
     * Verifies that the summary is not rendered without the status the advice contributes.
     */
    @Test
    void shouldThrowException_whenStatusIsNull() {
        // GIVEN
        var model = new ExtendedModelMap();

        // WHEN
        var exception = catchThrowable( () -> underTest.showServerState( null, model ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "serverStatus" );
    }

    /**
     * Verifies that the summary is not rendered without a model.
     */
    @Test
    void shouldThrowException_whenModelIsNull() {
        // GIVEN
        // no model

        // WHEN
        var exception = catchThrowable( () -> underTest.showServerState( ServerStatus.stopped(), null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "model" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static LaunchProfile profile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, false, "", "valheim-data", 1800, 4,
                7200, 43200, WorldPreset.EASY, Map.of(), Set.of() );
    }
}
