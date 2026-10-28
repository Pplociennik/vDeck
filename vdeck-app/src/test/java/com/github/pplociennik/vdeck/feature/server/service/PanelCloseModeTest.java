package com.github.pplociennik.vdeck.feature.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how {@link PanelCloseMode} interprets the values stored in {@code vDeck.properties}.
 */
class PanelCloseModeTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every stored value, in any letter case and with surrounding white space, finds its mode.
     *
     * @param value
     *         stored value
     * @param expected
     *         mode the value names
     */
    @ParameterizedTest( name = "[{index}] value=''{0}'' finds {1}" )
    @CsvSource( { "stop, STOP", "graceful, GRACEFUL", "leave, LEAVE", "'  Stop ', STOP", "LEAVE, LEAVE" } )
    void shouldFindMode_whenValueNamesIt( String value, PanelCloseMode expected ) {
        // GIVEN
        // a value as written in the settings file

        // WHEN
        Optional< PanelCloseMode > result = PanelCloseMode.fromValue( value );

        // THEN
        assertThat( result ).contains( expected );
    }

    /**
     * Verifies that a missing, empty or unknown value names no mode, so the caller can apply its default.
     *
     * @param value
     *         stored value naming no mode
     */
    @ParameterizedTest( name = "[{index}] value=''{0}'' finds nothing" )
    @NullAndEmptySource
    @ValueSource( strings = { "   ", "kill", "stopp" } )
    void shouldFindNothing_whenValueNamesNoMode( String value ) {
        // GIVEN
        // a value naming no mode

        // WHEN
        Optional< PanelCloseMode > result = PanelCloseMode.fromValue( value );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that every mode exposes the lower-case value under which it is stored and found again.
     */
    @Test
    void shouldRoundTripValue_whenModeIsStored() {
        // GIVEN
        PanelCloseMode[] modes = PanelCloseMode.values();

        // WHEN
        // each mode is looked up by its own value

        // THEN
        for ( PanelCloseMode mode : modes ) {
            assertThat( mode.getValue() ).isEqualTo( mode.name().toLowerCase( Locale.ROOT ) );
            assertThat( PanelCloseMode.fromValue( mode.getValue() ) ).contains( mode );
        }
    }
}
