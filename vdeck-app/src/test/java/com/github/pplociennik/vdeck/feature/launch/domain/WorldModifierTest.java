package com.github.pplociennik.vdeck.feature.launch.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link WorldModifier} accepts exactly the values the dedicated server manual lists for
 * each modifier.
 */
class WorldModifierTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private WorldModifier underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Selects the raids modifier, whose value set is the only one containing {@code none}.
     */
    @BeforeEach
    void setUp() {
        underTest = WorldModifier.RAIDS;
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every value listed for the modifier is allowed.
     *
     * @param value
     *         raids value listed by the manual
     */
    @ParameterizedTest( name = "[{index}] value={0}" )
    @ValueSource( strings = { "none", "muchless", "less", "more", "muchmore" } )
    void shouldAllowValue_whenManualListsIt( String value ) {
        // GIVEN
        // a raids value from the manual

        // WHEN
        boolean result = underTest.isAllowed( value );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that a value of another modifier, a differently written value or no value is refused.
     *
     * @param value
     *         value the raids modifier does not accept
     */
    @ParameterizedTest( name = "[{index}] value=\"{0}\"" )
    @NullAndEmptySource
    @ValueSource( strings = { "most", "veryhard", "None" } )
    void shouldRefuseValue_whenManualDoesNotListIt( String value ) {
        // GIVEN
        // a value outside the raids value set

        // WHEN
        boolean result = underTest.isAllowed( value );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that each modifier is stored in an optional setting of the launch profile file, so the
     * modifier can be left at the game default.
     *
     * @param modifier
     *         declared modifier
     */
    @ParameterizedTest( name = "[{index}] modifier={0}" )
    @EnumSource( WorldModifier.class )
    void shouldUseOptionalSetting_whenModifierIsDeclared( WorldModifier modifier ) {
        // GIVEN
        underTest = modifier;

        // WHEN
        LaunchProperty property = underTest.getProperty();

        // THEN
        assertThat( property.isMandatory() ).isFalse();
        assertThat( property.getName() ).startsWith( "world.modifier." );
    }

    /**
     * Verifies that the value list exposed for the screen cannot be modified.
     */
    @Test
    void shouldRejectModification_whenAllowedValuesAreRead() {
        // GIVEN
        var allowedValues = underTest.getAllowedValues();

        // WHEN
        Throwable thrown = catchThrowable( () -> allowedValues.add( "most" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }
}