package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.InvalidStartSettingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the start settings in {@code vDeck-start.properties} (AD-026).
 */
class StartSettingsTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Template shipped in the distribution; the test runs with the module directory as working directory.
     */
    private static final Path DISTRIBUTION_TEMPLATE = Path.of( "../.dist/.vDeck/config/vDeck-start.properties" );

    @TempDir
    private Path installation;

    private Path file;

    private StartSettings underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Resolves the settings file of a temporary installation.
     */
    @BeforeEach
    void setUp() {
        file = installation.resolve( ".vDeck/config/vDeck-start.properties" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a missing file is created with the safe defaults.
     */
    @Test
    void shouldCreateDefaults_whenFileIsMissing() throws Exception {
        // GIVEN
        // No settings file exists.

        // WHEN
        underTest = StartSettings.load( installation );

        // THEN
        assertThat( underTest ).isEqualTo( new StartSettings( "127.0.0.1", 8085, true ) );
        assertThat( file ).exists();
        assertThat( Files.readString( file, StandardCharsets.US_ASCII ) ).contains( "update.enabled=true" );
    }

    /**
     * Verifies that configured values are read, including disabled automatic updates.
     */
    @Test
    void shouldReadValues_whenFileIsValid() throws Exception {
        // GIVEN
        write( "panel.address=0.0.0.0\npanel.port=9090\nupdate.enabled=false\n" );

        // WHEN
        underTest = StartSettings.load( installation );

        // THEN
        assertThat( underTest ).isEqualTo( new StartSettings( "0.0.0.0", 9090, false ) );
    }

    /**
     * Verifies that missing keys take their defaults.
     */
    @Test
    void shouldUseDefault_whenKeyIsMissing() throws Exception {
        // GIVEN
        write( "panel.port=9090\n" );

        // WHEN
        underTest = StartSettings.load( installation );

        // THEN
        assertThat( underTest ).isEqualTo( new StartSettings( "127.0.0.1", 9090, true ) );
    }

    /**
     * Verifies that an invalid value stops the start and names only the key.
     *
     * @param contents
     *         settings file contents
     * @param key
     *         key expected in the rejection
     */
    @ParameterizedTest( name = "[{index}] {0}" )
    @CsvSource( delimiter = '|', value = { "panel.port=0|panel.port", "panel.port=65536|panel.port",
            "panel.port=http|panel.port", "panel.address=256.1.1.1|panel.address", "panel.address=bad host|panel.address",
            "panel.address=-host|panel.address", "update.enabled=yes|update.enabled",
            "update.enabled=TRUE|update.enabled" } )
    void shouldRejectValue_whenSettingIsInvalid( String contents, String key ) throws Exception {
        // GIVEN
        write( contents + "\n" );

        // WHEN
        var failure = catchThrowable( () -> StartSettings.load( installation ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying( InvalidStartSettingException.class,
                exception -> assertThat( exception.getKey() ).isEqualTo( key ) );
    }

    /**
     * Verifies the accepted address forms.
     *
     * @param address
     *         valid listen address
     */
    @ParameterizedTest( name = "[{index}] address={0}" )
    @ValueSource( strings = { "127.0.0.1", "192.168.1.20", "::1", "fe80::1", "localhost", "panel.example.org" } )
    void shouldAcceptAddress_whenFormIsValid( String address ) {
        // GIVEN
        // No additional setup.

        // WHEN
        underTest = new StartSettings( address, 8085, true );

        // THEN
        assertThat( underTest.address() ).isEqualTo( address );
    }

    /**
     * Verifies that an IPv6 literal is bracketed in the panel URL.
     */
    @Test
    void shouldBracketIpv6_whenBuildingUrlHost() {
        // GIVEN
        underTest = new StartSettings( "::1", 8085, true );

        // WHEN
        String host = underTest.urlHost();

        // THEN
        assertThat( host ).isEqualTo( "[::1]" );
        assertThat( new StartSettings( "127.0.0.1", 1, true ).urlHost() ).isEqualTo( "127.0.0.1" );
    }

    /**
     * Verifies that the distribution template holds exactly the keys and defaults of the launcher, so an
     * update never adds or removes a key the launcher does not know (AD-025).
     */
    @Test
    void shouldMatchDefaults_whenComparedWithDistributionTemplate() throws Exception {
        // GIVEN
        Properties template = new Properties();
        try ( Reader reader = Files.newBufferedReader( DISTRIBUTION_TEMPLATE, StandardCharsets.US_ASCII ) ) {
            template.load( reader );
        }

        // WHEN
        Map< String, String > values = new HashMap<>();
        template.stringPropertyNames().forEach( key -> values.put( key, template.getProperty( key ) ) );

        // THEN
        assertThat( values ).isEqualTo( StartSettings.DEFAULTS );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingInstallation = catchThrowable( () -> StartSettings.load( null ) );
        var missingAddress = catchThrowable( () -> new StartSettings( null, 8085, true ) );

        // THEN
        assertThat( missingInstallation ).isInstanceOf( NullPointerException.class );
        assertThat( missingAddress ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void write( String contents ) throws Exception {
        Files.createDirectories( file.getParent() );
        Files.writeString( file, contents );
    }
}
