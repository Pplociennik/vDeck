package com.github.pplociennik.vdeck.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the declaration of the settings stored in {@code vDeck.properties} and the default value
 * set derived from it.
 */
class VdeckPropertyTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private VdeckProperty underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Selects the setting used by the tests covering a single declaration.
     */
    @BeforeEach
    void setUp() {
        underTest = VdeckProperty.GAME_LOG_DIRECTORY;
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the game log directory setting keeps the key and default documented in the design.
     */
    @Test
    void shouldExposeDocumentedKeyAndDefault_whenGameLogDirectoryIsDeclared() {
        // GIVEN
        // the game log directory setting

        // WHEN
        String name = underTest.getName();

        // THEN
        assertThat( name ).isEqualTo( "game.log.directory" );
        assertThat( underTest.getDefaultValue() ).isEqualTo( "vdeck-logs" );
    }

    /**
     * Verifies that no two settings share a key, which would make one of them unreachable.
     */
    @Test
    void shouldExposeUniqueNames_whenAllPropertiesAreDeclared() {
        // GIVEN
        VdeckProperty[] properties = VdeckProperty.values();

        // WHEN
        long distinctNames = Arrays.stream( properties ).map( VdeckProperty::getName ).distinct().count();

        // THEN
        assertThat( distinctNames ).isEqualTo( properties.length );
    }

    /**
     * Verifies that every declared setting carries a usable key and default value.
     */
    @ParameterizedTest( name = "[{index}] property={0}" )
    @EnumSource( VdeckProperty.class )
    void shouldExposeNonBlankNameAndDefault_whenPropertyIsDeclared( VdeckProperty property ) {
        // GIVEN
        // a declared setting

        // WHEN
        String name = property.getName();

        // THEN
        assertThat( name ).isNotBlank();
        assertThat( property.getDefaultValue() ).isNotBlank();
    }

    /**
     * Verifies that the default value set describes every declared setting, so a bootstrapped file is
     * complete.
     */
    @Test
    void shouldContainEveryProperty_whenDefaultsAreCreated() {
        // GIVEN
        // the declared settings

        // WHEN
        Map< SystemPropertyIf, String > defaults = VdeckProperty.defaults();

        // THEN
        assertThat( defaults ).containsOnlyKeys( VdeckProperty.values() );
        assertThat( defaults ).containsEntry( underTest, "vdeck-logs" );
    }

    /**
     * Verifies that the default value set cannot be modified by a caller.
     */
    @Test
    void shouldRejectModification_whenDefaultsAreCreated() {
        // GIVEN
        Map< SystemPropertyIf, String > defaults = VdeckProperty.defaults();

        // WHEN
        Throwable thrown = catchThrowable( () -> defaults.put( underTest, "other" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }
}
