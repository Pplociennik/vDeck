package com.github.pplociennik.vdeck.feature.launch.service.impl;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifies how {@link GameSettingsDisplayServiceImpl} reads and stores the visibility of the advanced
 * game settings in {@code vDeck.properties}.
 */
@ExtendWith( MockitoExtension.class )
class GameSettingsDisplayServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private SystemPropertiesService vdeckProperties;

    private GameSettingsDisplayServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service backed by an isolated properties service.
     */
    @BeforeEach
    void setUp() {
        underTest = new GameSettingsDisplayServiceImpl( vdeckProperties );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a stored boolean is read regardless of letter case.
     *
     * @param stored
     *         value in the file
     * @param expected
     *         visibility read from it
     */
    @ParameterizedTest( name = "[{index}] stored={0}" )
    @CsvSource( { "true, true", "TRUE, true", "false, false" } )
    void shouldReadStoredVisibility_whenValueIsBoolean( String stored, boolean expected ) {
        // GIVEN
        given( vdeckProperties.getValue( VdeckProperty.GAME_SETTINGS_ADVANCED ) ).willReturn( stored );

        // WHEN
        boolean result = underTest.isAdvancedShown();

        // THEN
        assertThat( result ).isEqualTo( expected );
    }

    /**
     * Verifies that a hand-edited value which is not a boolean keeps the section hidden.
     */
    @Test
    void shouldKeepSectionHidden_whenStoredValueIsNotBoolean() {
        // GIVEN
        given( vdeckProperties.getValue( VdeckProperty.GAME_SETTINGS_ADVANCED ) ).willReturn( "yes" );

        // WHEN
        boolean result = underTest.isAdvancedShown();

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that the choice is stored as a boolean in the panel's settings file.
     */
    @Test
    void shouldStoreVisibility_whenChoiceIsMade() {
        // GIVEN
        // a writable settings file

        // WHEN
        boolean result = underTest.setAdvancedShown( true );

        // THEN
        assertThat( result ).isTrue();
        verify( vdeckProperties ).setValue( VdeckProperty.GAME_SETTINGS_ADVANCED, "true" );
    }

    /**
     * Verifies that a failed write is reported as a result instead of an exception.
     */
    @Test
    void shouldReportFailure_whenFileCannotBeWritten() {
        // GIVEN
        willThrow( new UncheckedIOException( "disk full", new IOException( "disk full" ) ) )
                .given( vdeckProperties ).setValue( VdeckProperty.GAME_SETTINGS_ADVANCED, "false" );

        // WHEN
        boolean result = underTest.setAdvancedShown( false );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that the constructor rejects a missing properties service.
     */
    @Test
    void shouldThrowException_whenPropertiesServiceIsNull() {
        // GIVEN
        // no properties service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new GameSettingsDisplayServiceImpl( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "vdeckProperties must not be null" );
    }
}
