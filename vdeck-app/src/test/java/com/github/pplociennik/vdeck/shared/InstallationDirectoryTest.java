package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies which installation directory {@link InstallationDirectory} selects before Spring starts. The
 * class is a static utility, so the tests call it directly and need no {@code underTest} instance.
 */
class InstallationDirectoryTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Path APPLICATION_HOME = Path.of( "home" ).toAbsolutePath();

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the override wins over the application home, with surrounding white space removed.
     */
    @Test
    void shouldUseOverride_whenServerDirectoryIsSet() {
        // GIVEN
        Path sandbox = Path.of( "sandbox" ).toAbsolutePath();
        Properties properties = new Properties();
        properties.setProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, "  " + sandbox + " " );

        // WHEN
        Path result = InstallationDirectory.resolve( properties, APPLICATION_HOME );

        // THEN
        assertThat( result ).isEqualTo( sandbox );
    }

    /**
     * Verifies that a missing or blank override falls back to the application home.
     *
     * @param value
     *         blank override
     */
    @ParameterizedTest( name = "[{index}] override=''{0}'' uses the application home" )
    @ValueSource( strings = { "", "   " } )
    void shouldUseApplicationHome_whenOverrideIsBlank( String value ) {
        // GIVEN
        Properties properties = new Properties();
        properties.setProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, value );

        // WHEN
        Path result = InstallationDirectory.resolve( properties, APPLICATION_HOME );

        // THEN
        assertThat( result ).isEqualTo( APPLICATION_HOME );
    }

    /**
     * Verifies that the application home is used when no override exists at all.
     */
    @Test
    void shouldUseApplicationHome_whenOverrideIsMissing() {
        // GIVEN
        Properties properties = new Properties();

        // WHEN
        Path result = InstallationDirectory.resolve( properties, APPLICATION_HOME );

        // THEN
        assertThat( result ).isEqualTo( APPLICATION_HOME );
    }

    /**
     * Verifies that missing arguments are rejected.
     */
    @Test
    void shouldRejectNull_whenArgumentIsMissing() {
        // GIVEN
        Properties properties = new Properties();

        // WHEN
        var missingProperties = catchThrowable( () -> InstallationDirectory.resolve( null, APPLICATION_HOME ) );
        var missingHome = catchThrowable( () -> InstallationDirectory.resolve( properties, null ) );

        // THEN
        assertThat( missingProperties ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemProperties" );
        assertThat( missingHome ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "applicationHome" );
    }
}
