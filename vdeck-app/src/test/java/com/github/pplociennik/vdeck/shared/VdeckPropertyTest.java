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
        assertThat( underTest.getDefaultValue() ).isEqualTo( ".vDeck/log" );
        assertThat( underTest.isMandatory() ).isTrue();
    }

    /**
     * Verifies that game server logs have a retention window by default, so one file per launch cannot
     * accumulate without bound.
     */
    @Test
    void shouldExposeDocumentedKeyAndDefault_whenGameLogRetentionIsDeclared() {
        // GIVEN
        VdeckProperty property = VdeckProperty.GAME_LOG_RETENTION_DAYS;

        // WHEN
        String name = property.getName();

        // THEN
        assertThat( name ).isEqualTo( "game.log.retention.days" );
        assertThat( property.getDefaultValue() ).isEqualTo( "14" );
        assertThat( property.isMandatory() ).isTrue();
    }

    /**
     * Verifies that the publisher's start script is named by a setting, so an installation whose script
     * has a different name can still be read.
     */
    @Test
    void shouldExposeDocumentedKeyAndDefault_whenStartScriptIsDeclared() {
        // GIVEN
        VdeckProperty property = VdeckProperty.SERVER_START_SCRIPT;

        // WHEN
        String name = property.getName();

        // THEN
        assertThat( name ).isEqualTo( "server.start.script" );
        assertThat( property.getDefaultValue() ).isEqualTo( "start_headless_server.bat" );
        assertThat( property.isMandatory() ).isTrue();
    }

    /**
     * Verifies that the remembered visibility of the advanced game settings starts hidden and is always
     * present in the file.
     */
    @Test
    void shouldExposeHiddenDefault_whenGameSettingsAdvancedIsDeclared() {
        // GIVEN
        VdeckProperty property = VdeckProperty.GAME_SETTINGS_ADVANCED;

        // WHEN
        String name = property.getName();

        // THEN
        assertThat( name ).isEqualTo( "panel.game.settings.advanced" );
        assertThat( property.getDefaultValue() ).isEqualTo( "false" );
        assertThat( property.isMandatory() ).isTrue();
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
     * Verifies that every declared setting carries a usable key, and that a mandatory setting also
     * carries a usable default value.
     *
     * @param property
     *         declared setting
     */
    @ParameterizedTest( name = "[{index}] property={0}" )
    @EnumSource( VdeckProperty.class )
    void shouldExposeNonBlankNameAndMandatoryDefault_whenPropertyIsDeclared( VdeckProperty property ) {
        // GIVEN
        // a declared setting

        // WHEN
        String name = property.getName();

        // THEN
        assertThat( name ).isNotBlank();
        if ( property.isMandatory() ) {
            assertThat( property.getDefaultValue() ).isNotBlank();
        }
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
        Map< SystemPropertyIf, String > defaults = VdeckProperty.defaults().asMap();

        // THEN
        assertThat( defaults ).containsOnlyKeys( VdeckProperty.values() );
        assertThat( defaults ).containsEntry( underTest, ".vDeck/log" );
    }

    /**
     * Verifies that the entries of the default value set cannot be modified through their map view.
     */
    @Test
    void shouldRejectModification_whenDefaultsAreCreated() {
        // GIVEN
        Map< SystemPropertyIf, String > defaults = VdeckProperty.defaults().asMap();

        // WHEN
        Throwable thrown = catchThrowable( () -> defaults.put( underTest, "other" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }
}
