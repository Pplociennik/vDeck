package com.github.pplociennik.vdeck.feature.launch.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link WorldPreset} finds a preset by the value written on the server command line.
 */
class WorldPresetTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private WorldPreset underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every preset is found by its own command-line value.
     *
     * @param preset
     *         preset looked up by its value
     */
    @ParameterizedTest( name = "[{index}] preset={0}" )
    @EnumSource( WorldPreset.class )
    void shouldFindPreset_whenLookedUpByItsCliValue( WorldPreset preset ) {
        // GIVEN
        String cliValue = preset.getCliValue();

        // WHEN
        underTest = WorldPreset.fromCliValue( cliValue ).orElseThrow();

        // THEN
        assertThat( underTest ).isEqualTo( preset );
    }

    /**
     * Verifies that the lookup ignores letter case and surrounding whitespace, because the manual lists
     * the presets capitalised.
     *
     * @param cliValue
     *         differently written value of the hard preset
     */
    @ParameterizedTest( name = "[{index}] value=\"{0}\"" )
    @ValueSource( strings = { "Hard", "HARD", " hard " } )
    void shouldFindPreset_whenValueDiffersInCaseOrWhitespace( String cliValue ) {
        // GIVEN
        // a value naming the hard preset

        // WHEN
        var result = WorldPreset.fromCliValue( cliValue );

        // THEN
        assertThat( result ).contains( WorldPreset.HARD );
    }

    /**
     * Verifies that a value naming no preset, including a missing one, finds nothing.
     *
     * @param cliValue
     *         value naming no preset
     */
    @ParameterizedTest( name = "[{index}] value=\"{0}\"" )
    @NullAndEmptySource
    @ValueSource( strings = { "legendary", "veryhard" } )
    void shouldFindNothing_whenValueNamesNoPreset( String cliValue ) {
        // GIVEN
        // a value that is not a preset

        // WHEN
        var result = WorldPreset.fromCliValue( cliValue );

        // THEN
        assertThat( result ).isEmpty();
    }
}