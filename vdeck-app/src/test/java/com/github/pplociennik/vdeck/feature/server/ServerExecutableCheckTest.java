package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies when the panel start is stopped for a missing {@code valheim_server.exe}.
 */
class ServerExecutableCheckTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path installation;

    @TempDir
    private Path applicationHome;

    private Properties properties;

    private ByteArrayOutputStream output;

    private PrintStream console;

    private ServerExecutableCheck underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the check with the installation directory override pointing at an empty directory.
     */
    @BeforeEach
    void setUp() {
        properties = new Properties();
        properties.setProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, installation.toString() );
        output = new ByteArrayOutputStream();
        console = new PrintStream( output, true, StandardCharsets.UTF_8 );
        underTest = new ServerExecutableCheck( properties, applicationHome, console );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the check passes silently when the executable is a regular file in the installation.
     */
    @Test
    void shouldPass_whenExecutableExists() throws IOException {
        // GIVEN
        Files.writeString( installation.resolve( "valheim_server.exe" ), "fixture" );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isTrue();
        assertThat( printed() ).isEmpty();
    }

    /**
     * Verifies that a missing executable fails the check and tells the user which directory lacks it.
     */
    @Test
    void shouldFailAndInformUser_whenExecutableIsMissing() {
        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
        assertThat( printed() ).contains( "valheim_server.exe" ).contains( installation.toString() );
    }

    /**
     * Verifies that a directory named like the executable does not count as the server file.
     */
    @Test
    void shouldFail_whenExecutableIsDirectory() throws IOException {
        // GIVEN
        Files.createDirectory( installation.resolve( "valheim_server.exe" ) );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that without the installation directory override the application home is checked.
     */
    @Test
    void shouldCheckApplicationHome_whenNoDirectoryOverrideIsSet() throws IOException {
        // GIVEN
        properties.remove( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        Files.writeString( applicationHome.resolve( "valheim_server.exe" ), "fixture" );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that a blank installation directory override falls back to the application home.
     */
    @Test
    void shouldCheckApplicationHome_whenDirectoryOverrideIsBlank() {
        // GIVEN
        properties.setProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, "  " );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
        assertThat( printed() ).contains( applicationHome.toString() );
    }

    /**
     * Verifies that the switch set to false, in any letter case, skips the check without a message.
     *
     * @param value
     *         value of the switch
     */
    @ParameterizedTest( name = "[{index}] switch={0} disables the check" )
    @ValueSource( strings = { "false", "FALSE", " false " } )
    void shouldPassWithoutChecking_whenCheckIsDisabled( String value ) {
        // GIVEN
        properties.setProperty( ServerExecutableCheck.ENABLED_PROPERTY, value );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isTrue();
        assertThat( printed() ).isEmpty();
    }

    /**
     * Verifies that any switch value other than false keeps the check enabled.
     *
     * @param value
     *         value of the switch
     */
    @ParameterizedTest( name = "[{index}] switch={0} keeps the check" )
    @ValueSource( strings = { "true", "", "no", "0" } )
    void shouldStillCheck_whenSwitchIsNotFalse( String value ) {
        // GIVEN
        properties.setProperty( ServerExecutableCheck.ENABLED_PROPERTY, value );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that the setting in {@code vDeck.properties} set to false, in any letter case, skips the check
     * without a message.
     *
     * @param value
     *         value of the setting
     */
    @ParameterizedTest( name = "[{index}] server.file.check={0} disables the check" )
    @ValueSource( strings = { "false", "FALSE", " false " } )
    void shouldPassWithoutChecking_whenSettingIsFalse( String value ) throws IOException {
        // GIVEN
        writeSettings( "server.file.check=" + value );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isTrue();
        assertThat( printed() ).isEmpty();
    }

    /**
     * Verifies that a settings file whose setting is not false, or that lacks it, keeps the check enabled.
     *
     * @param contents
     *         contents of {@code vDeck.properties}
     */
    @ParameterizedTest( name = "[{index}] settings \"{0}\" keep the check" )
    @ValueSource( strings = { "server.file.check=true", "server.file.check=", "server.file.check=no",
            "panel.open.browser=false" } )
    void shouldStillCheck_whenSettingIsNotFalse( String contents ) throws IOException {
        // GIVEN
        writeSettings( contents );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that an unreadable settings path, here a directory, keeps the check enabled instead of
     * failing the start.
     */
    @Test
    void shouldStillCheck_whenSettingsFileIsUnreadable() throws IOException {
        // GIVEN
        Files.createDirectories( installation.resolve( ".vDeck/config/vDeck.properties" ) );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isFalse();
        assertThat( printed() ).contains( "valheim_server.exe" );
    }

    /**
     * Verifies that the setting is read from the application home when no directory override is set.
     */
    @Test
    void shouldReadSettingFromApplicationHome_whenNoDirectoryOverrideIsSet() throws IOException {
        // GIVEN
        properties.remove( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        Path file = applicationHome.resolve( ".vDeck/config/vDeck.properties" );
        Files.createDirectories( file.getParent() );
        Files.writeString( file, "server.file.check=false" );

        // WHEN
        boolean result = underTest.passes();

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that null system properties are rejected.
     */
    @Test
    void shouldThrowException_whenPropertiesAreNull() {
        // WHEN
        Throwable exception = catchThrowable( () -> new ServerExecutableCheck( null, applicationHome, console ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "systemProperties" );
    }

    /**
     * Verifies that a null application home is rejected.
     */
    @Test
    void shouldThrowException_whenApplicationHomeIsNull() {
        // WHEN
        Throwable exception = catchThrowable( () -> new ServerExecutableCheck( properties, null, console ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "applicationHome" );
    }

    /**
     * Verifies that a null console is rejected.
     */
    @Test
    void shouldThrowException_whenConsoleIsNull() {
        // WHEN
        Throwable exception = catchThrowable( () -> new ServerExecutableCheck( properties, applicationHome, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "console" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private String printed() {
        return output.toString( StandardCharsets.UTF_8 );
    }

    private void writeSettings( String contents ) throws IOException {
        Path file = installation.resolve( ".vDeck/config/vDeck.properties" );
        Files.createDirectories( file.getParent() );
        Files.writeString( file, contents );
    }
}
