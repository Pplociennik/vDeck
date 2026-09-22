package com.github.pplociennik.vdeck.feature.launch.domain;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the declaration of the settings stored in {@code valheim-server.properties} and the default
 * value set derived from it.
 */
class LaunchPropertyTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LaunchProperty underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Selects the setting used by the tests covering a single declaration.
     */
    @BeforeEach
    void setUp() {
        underTest = LaunchProperty.SERVER_PORT;
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the port setting keeps the key and the default documented by the server manual.
     */
    @Test
    void shouldExposeDocumentedKeyAndDefault_whenPortIsDeclared() {
        // GIVEN
        // the port setting

        // WHEN
        String name = underTest.getName();

        // THEN
        assertThat( name ).isEqualTo( "server.port" );
        assertThat( underTest.getDefaultValue() ).isEqualTo( "2456" );
        assertThat( underTest.isMandatory() ).isTrue();
    }

    /**
     * Verifies that no two settings share a key, which would make one of them unreachable.
     */
    @Test
    void shouldExposeUniqueNames_whenAllPropertiesAreDeclared() {
        // GIVEN
        LaunchProperty[] properties = LaunchProperty.values();

        // WHEN
        long distinctNames = Arrays.stream( properties ).map( LaunchProperty::getName ).distinct().count();

        // THEN
        assertThat( distinctNames ).isEqualTo( properties.length );
    }

    /**
     * Verifies that a mandatory setting has a usable default and an optional one defaults to empty,
     * which is how an unused command-line option is stored.
     *
     * @param property
     *         declared setting
     */
    @ParameterizedTest( name = "[{index}] property={0}" )
    @EnumSource( LaunchProperty.class )
    void shouldMatchDefaultToMandatoryFlag_whenPropertyIsDeclared( LaunchProperty property ) {
        // GIVEN
        // a declared setting

        // WHEN
        String defaultValue = property.getDefaultValue();

        // THEN
        assertThat( property.getName() ).isNotBlank();
        if ( property.isMandatory() ) {
            assertThat( defaultValue ).isNotBlank();
        } else {
            assertThat( defaultValue ).isEmpty();
        }
    }

    /**
     * Verifies that the secret and the options that may be omitted from the command line are optional.
     */
    @Test
    void shouldDeclareOmittableOptionsAsOptional_whenAllPropertiesAreDeclared() {
        // GIVEN
        LaunchProperty[] properties = LaunchProperty.values();

        // WHEN
        var optional = Arrays.stream( properties ).filter( property -> !property.isMandatory() ).toList();

        // THEN
        assertThat( optional ).containsExactlyInAnyOrder( LaunchProperty.SERVER_PASSWORD,
                LaunchProperty.SERVER_INSTANCE_ID, LaunchProperty.WORLD_PRESET,
                LaunchProperty.WORLD_MODIFIER_COMBAT, LaunchProperty.WORLD_MODIFIER_DEATH_PENALTY,
                LaunchProperty.WORLD_MODIFIER_RESOURCES, LaunchProperty.WORLD_MODIFIER_RAIDS,
                LaunchProperty.WORLD_MODIFIER_PORTALS );
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
        Map< SystemPropertyIf, String > defaults = LaunchProperty.defaults().asMap();

        // THEN
        assertThat( defaults ).containsOnlyKeys( LaunchProperty.values() );
        assertThat( defaults ).containsEntry( underTest, "2456" );
    }

    /**
     * Verifies that the entries of the default value set cannot be modified through their map view.
     */
    @Test
    void shouldRejectModification_whenDefaultsAreCreated() {
        // GIVEN
        Map< SystemPropertyIf, String > defaults = LaunchProperty.defaults().asMap();

        // WHEN
        Throwable thrown = catchThrowable( () -> defaults.put( underTest, "other" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }
}