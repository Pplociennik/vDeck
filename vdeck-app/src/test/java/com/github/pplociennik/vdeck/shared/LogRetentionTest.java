package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Verifies how {@link LogRetention} turns a retention switch and a number of days into the oldest kept day.
 * The class is a static utility, so the tests call it directly and need no {@code underTest} instance.
 */
@ExtendWith( MockitoExtension.class )
class LogRetentionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDate TODAY = LocalDate.of( 2026, 10, 28 );

    private static final VdeckProperty ENABLED = VdeckProperty.PANEL_LOG_RETENTION_ENABLED;

    private static final VdeckProperty DAYS = VdeckProperty.PANEL_LOG_RETENTION_DAYS;

    @Mock
    private SystemPropertiesService service;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a positive number of days keeps today and that many days before it.
     *
     * @param days
     *         stored number of days
     * @param oldestKept
     *         expected oldest kept day
     */
    @ParameterizedTest( name = "[{index}] days={0} keeps logs from {1}" )
    @CsvSource( { "1, 2026-10-27", "14, 2026-10-14", "30, 2026-09-28" } )
    void shouldKeepWindow_whenDaysArePositive( String days, LocalDate oldestKept ) {
        // GIVEN
        givenSettings( "true", days );

        // WHEN
        Optional< LocalDate > result = LogRetention.oldestKeptDay( service, ENABLED, DAYS, TODAY );

        // THEN
        assertThat( result ).contains( oldestKept );
    }

    /**
     * Verifies that zero days keep every log although the retention switch is on.
     */
    @Test
    void shouldKeepEveryLog_whenDaysAreZero() {
        // GIVEN
        givenSettings( "true", "0" );

        // WHEN
        Optional< LocalDate > result = LogRetention.oldestKeptDay( service, ENABLED, DAYS, TODAY );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a switched-off retention keeps every log without reading the number of days.
     */
    @Test
    void shouldKeepEveryLog_whenRetentionIsSwitchedOff() {
        // GIVEN
        given( service.getValue( VdeckProperty.FILE_NAME, ENABLED ) ).willReturn( "false" );

        // WHEN
        Optional< LocalDate > result = LogRetention.oldestKeptDay( service, ENABLED, DAYS, TODAY );

        // THEN
        assertThat( result ).isEmpty();
        then( service ).should( never() ).getValue( VdeckProperty.FILE_NAME, DAYS );
    }

    /**
     * Verifies that a negative or non-numeric number of days falls back to the setting's default of 30 days
     * instead of removing today's logs.
     *
     * @param days
     *         stored invalid number of days
     */
    @ParameterizedTest( name = "[{index}] days=''{0}'' falls back to the default" )
    @ValueSource( strings = { "-1", "many" } )
    void shouldUseDefaultDays_whenDaysAreInvalid( String days ) {
        // GIVEN
        givenSettings( "true", days );

        // WHEN
        Optional< LocalDate > result = LogRetention.oldestKeptDay( service, ENABLED, DAYS, TODAY );

        // THEN
        assertThat( result ).contains( TODAY.minusDays( 30 ) );
    }

    /**
     * Verifies that a switch which is neither {@code true} nor {@code false} falls back to its default, on.
     */
    @Test
    void shouldApplyRetention_whenSwitchIsNotBoolean() {
        // GIVEN
        givenSettings( "maybe", "14" );

        // WHEN
        Optional< LocalDate > result = LogRetention.oldestKeptDay( service, ENABLED, DAYS, TODAY );

        // THEN
        assertThat( result ).contains( TODAY.minusDays( 14 ) );
    }

    /**
     * Verifies that every missing argument is rejected before a setting is read.
     */
    @Test
    void shouldRejectNull_whenArgumentIsMissing() {
        // GIVEN
        // no settings

        // WHEN
        var missingService = catchThrowable( () -> LogRetention.oldestKeptDay( null, ENABLED, DAYS, TODAY ) );
        var missingEnabled = catchThrowable( () -> LogRetention.oldestKeptDay( service, null, DAYS, TODAY ) );
        var missingDays = catchThrowable( () -> LogRetention.oldestKeptDay( service, ENABLED, null, TODAY ) );
        var missingToday = catchThrowable( () -> LogRetention.oldestKeptDay( service, ENABLED, DAYS, null ) );

        // THEN
        assertThat( missingService ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "service" );
        assertThat( missingEnabled ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "enabled" );
        assertThat( missingDays ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "days" );
        assertThat( missingToday ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "today" );
        then( service ).shouldHaveNoInteractions();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenSettings( String enabled, String days ) {
        given( service.getValue( VdeckProperty.FILE_NAME, ENABLED ) ).willReturn( enabled );
        given( service.getValue( VdeckProperty.FILE_NAME, DAYS ) ).willReturn( days );
    }
}
