package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the settings templates shipped in the distribution hold exactly the keys and default
 * values of the panel's settings enums. The updater makes every installation match these templates, so a
 * key missing here would be removed from users' files and a stale key would be kept forever (AD-025).
 */
class DistributionTemplatesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Template directory; the test runs with the module directory as working directory.
     */
    private static final Path TEMPLATES = Path.of( "../.dist/.vDeck/config" );

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the template of {@code vDeck.properties} against {@link VdeckProperty}.
     */
    @Test
    void shouldMatchPanelSettings_whenComparedWithEnum() throws Exception {
        // GIVEN
        Map< String, String > expected = new HashMap<>();
        for ( VdeckProperty property : VdeckProperty.values() ) {
            expected.put( property.getName(), property.getDefaultValue() );
        }

        // WHEN
        Map< String, String > template = read( VdeckProperty.FILE_NAME );

        // THEN
        assertThat( template ).isEqualTo( expected );
    }

    /**
     * Verifies the template of {@code valheim-server.properties} against {@link LaunchProperty}.
     */
    @Test
    void shouldMatchServerSettings_whenComparedWithEnum() throws Exception {
        // GIVEN
        Map< String, String > expected = new HashMap<>();
        for ( LaunchProperty property : LaunchProperty.values() ) {
            expected.put( property.getName(), property.getDefaultValue() );
        }

        // WHEN
        Map< String, String > template = read( LaunchProperty.FILE_NAME );

        // THEN
        assertThat( template ).isEqualTo( expected );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static Map< String, String > read( String fileName ) throws Exception {
        Properties properties = new Properties();
        try ( Reader reader = Files.newBufferedReader( TEMPLATES.resolve( fileName ), StandardCharsets.UTF_8 ) ) {
            properties.load( reader );
        }
        Map< String, String > values = new HashMap<>();
        properties.stringPropertyNames().forEach( key -> values.put( key, properties.getProperty( key ) ) );
        return values;
    }
}
