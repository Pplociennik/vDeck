package com.github.pplociennik.vdeck.feature.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that the launch profile becomes the right {@code valheim_server.exe} command line, that
 * optional settings left empty disappear rather than being passed as blanks, and above all that the
 * generated mirror script never contains the server password.
 */
@ExtendWith( MockitoExtension.class )
class LaunchCommandServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String PASSWORD = "verySecretPassword";

    private static final String SCRIPT_NAME = "start_headless_server.bat";

    private static final String SAVE_DIRECTORY = "valheim-data";

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private PathService pathService;

    @TempDir
    private Path installationDirectory;

    private Path logFile;

    private String password = "";

    private LaunchCommandServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the service and the paths every test resolves against.
     */
    @BeforeEach
    void setUp() {
        logFile = installationDirectory.resolve( ".vDeck" ).resolve( "log" ).resolve( "run.log" );
        underTest = new LaunchCommandServiceImpl( systemPropertiesService, pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that rendering uses the specification alone, without rereading changed properties. */
    @Test
    void shouldRenderCapturedArguments_whenSettingsAreUnavailable() {
        // GIVEN
        var spec = new LaunchSpec( installationDirectory.resolve( "valheim_server.exe" ),
                List.of( "-name", "Captured server", "-password", PASSWORD ),
                Map.of( "SteamAppId", "123456" ), installationDirectory, logFile );

        // WHEN
        var result = underTest.renderScript( spec );

        // THEN
        assertThat( result ).contains( "Captured server", "set SteamAppId=123456" ).doesNotContain( PASSWORD );
        org.mockito.Mockito.verifyNoInteractions( systemPropertiesService, pathService );
    }

    /** Verifies that an option-looking name does not interfere with redaction of the password. */
    @Test
    void shouldMaskPasswordArgument_whenServerNameLooksLikePasswordOption() {
        // GIVEN
        var spec = new LaunchSpec( installationDirectory.resolve( "valheim_server.exe" ),
                List.of( "-name", "-password", "-password", PASSWORD ),
                Map.of( "SteamAppId", "892970" ), installationDirectory, logFile );

        // WHEN
        var result = underTest.renderScript( spec );

        // THEN
        assertThat( result ).contains( "-name -password -password \"%VDECK_SERVER_PASSWORD%\"" )
                .doesNotContain( PASSWORD );
    }

    /** Verifies that rendering rejects an absent specification before reading its contents. */
    @Test
    void shouldRejectRendering_whenSpecificationIsNull() {
        // GIVEN
        // No specification was supplied.

        // WHEN
        var thrown = catchThrowable( () -> underTest.renderScript( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchSpec" );
    }

    /**
     * Verifies the full command line of a profile that uses every setting, including the order the
     * options are assembled in and the fixed headless flags the profile does not know about.
     */
    @Test
    void shouldBuildCompleteCommandLine_whenEverySettingIsUsed() {
        // GIVEN
        givenPassword( PASSWORD );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( fullProfile(), password ), logFile );

        // THEN
        assertThat( result.arguments() ).containsExactly(
                "-nographics", "-batchmode",
                "-name", "vDeck", "-password", PASSWORD, "-public", "1",
                "-port", "2456", "-crossplay", "-instanceid", "sevenseas",
                "-world", "Dedicated", "-savedir", savePath().toString(),
                "-saveinterval", "1800", "-backups", "4", "-backupshort", "7200", "-backuplong", "43200",
                "-preset", WorldPreset.values()[ 0 ].getCliValue(),
                "-modifier", "combat", "hard",
                "-modifier", "deathpenalty", "casual",
                "-setkey", "nobuildcost",
                "-setkey", "nomap",
                "-logFile", logFile.toString() );
    }

    /**
     * Verifies that the command-line names of multi-word modifiers and keys are the lower-case ones the
     * server understands, not the camel-case identifiers the settings screen uses.
     */
    @Test
    void shouldUseLowerCaseNames_whenModifiersAndKeysAreOnCommandLine() {
        // GIVEN
        givenPassword( "" );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( fullProfile(), password ), logFile );

        // THEN
        assertThat( result.arguments() )
                .contains( "deathpenalty", "nobuildcost", "nomap" )
                .doesNotContain( "deathPenalty", "noBuildCost", "noMap" );
    }

    /**
     * Verifies that settings left empty are omitted entirely, because an empty optional setting means
     * "let the server decide" rather than "pass an empty value".
     */
    @Test
    void shouldOmitEmptyOptionalSettings_whenProfileIsMinimal() {
        // GIVEN
        givenPassword( "" );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( result.arguments() ).containsExactly(
                "-nographics", "-batchmode",
                "-name", "vDeck", "-public", "0",
                "-port", "2456",
                "-world", "Dedicated", "-savedir", savePath().toString(),
                "-saveinterval", "1800", "-backups", "4", "-backupshort", "7200", "-backuplong", "43200",
                "-logFile", logFile.toString() );
        assertThat( result.arguments() )
                .doesNotContain( "-password", "-crossplay", "-instanceid", "-preset", "-modifier", "-setkey" );
    }

    /**
     * Verifies that the mirror script never contains the password, which is the whole reason it may be
     * opened, screenshotted or attached to a report.
     */
    @Test
    void shouldNeverWritePasswordIntoScript_whenPasswordIsConfigured() {
        // GIVEN
        givenPassword( PASSWORD );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( fullProfile(), password ), logFile );

        // THEN
        assertThat( underTest.renderScript( result ) ).doesNotContain( PASSWORD )
                .contains( "-password \"%VDECK_SERVER_PASSWORD%\"" )
                .contains( "set SteamAppId=892970" );
        assertThat( result.arguments() ).contains( PASSWORD );
    }

    /** Verifies that a password with spaces stays one argument and its script placeholder is quoted. */
    @Test
    void shouldQuotePasswordPlaceholder_whenPasswordContainsSpaces() {
        // GIVEN
        String password = "synthetic test password";
        givenPassword( password );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( fullProfile(), password ), logFile );

        // THEN
        assertThat( underTest.renderScript( result ) ).contains( "-password \"%VDECK_SERVER_PASSWORD%\"" )
                .doesNotContain( password );
        assertThat( result.arguments() ).containsSubsequence( "-password", password, "-public" );
    }

    /**
     * Verifies that a profile without a password produces a script that neither mentions the option nor
     * explains how to supply it.
     */
    @Test
    void shouldNotMentionPassword_whenProfileHasNone() {
        // GIVEN
        givenPassword( "" );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( underTest.renderScript( result ) ).doesNotContain( "VDECK_SERVER_PASSWORD" )
                .doesNotContain( "-password" );
    }

    /**
     * Verifies that the script stays pure ASCII, because {@code cmd.exe} reads it in an OEM code page
     * and would mangle anything else.
     */
    @Test
    void shouldContainOnlyAsciiCharacters_whenScriptIsGenerated() {
        // GIVEN
        givenPassword( PASSWORD );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( fullProfile(), password ), logFile );

        // THEN
        assertThat( underTest.renderScript( result ).chars().allMatch( character -> character < 128 ) ).isTrue();
    }

    /**
     * Verifies that the Steam application identifier is taken from the publisher's script, so a change
     * to it does not need a new panel release.
     */
    @Test
    void shouldReadSteamAppIdFromPublisherScript_whenItDeclaresOne() {
        // GIVEN
        givenPassword( "" );
        givenStartScriptDeclaring( "123456" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( result.environment() ).containsEntry( "SteamAppId", "123456" );
    }

    /**
     * Verifies that a missing publisher script does not stop a launch: the built-in identifier is used
     * instead, because the server cannot start without one at all.
     */
    @Test
    void shouldFallBackToBuiltInSteamAppId_whenScriptIsMissing() {
        // GIVEN
        givenPassword( "" );
        givenConfiguredStartScriptName();
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( result.environment() )
                .containsEntry( "SteamAppId", LaunchCommandServiceImpl.DEFAULT_STEAM_APP_ID );
    }

    /**
     * Verifies that a publisher script which says nothing about the identifier is handled like a
     * missing one.
     *
     * @throws IOException
     *         if the script cannot be prepared
     */
    @Test
    void shouldFallBackToBuiltInSteamAppId_whenScriptDeclaresNone() throws IOException {
        // GIVEN
        givenPassword( "" );
        givenConfiguredStartScriptName();
        Files.writeString( installationDirectory.resolve( SCRIPT_NAME ),
                "@echo off\r\nvalheim_server -nographics\r\n", StandardCharsets.ISO_8859_1 );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( result.environment() )
                .containsEntry( "SteamAppId", LaunchCommandServiceImpl.DEFAULT_STEAM_APP_ID );
    }

    /**
     * Verifies that the executable and working directory come from the installation directory, so the
     * server runs beside its own files.
     */
    @Test
    void shouldRunExecutableFromInstallationDirectory_whenCommandIsBuilt() {
        // GIVEN
        givenPassword( "" );
        givenStartScriptDeclaring( "892970" );
        givenResolvedSaveDirectory();

        // WHEN
        LaunchSpec result = underTest.build( new LaunchConfiguration( minimalProfile(), password ), logFile );

        // THEN
        assertThat( result.executable() )
                .isEqualTo( installationDirectory.resolve( "valheim_server.exe" ) );
        assertThat( result.workingDirectory() ).isEqualTo( installationDirectory );
        assertThat( result.logFile() ).isEqualTo( logFile );
    }

    /**
     * Verifies that a null profile is rejected before anything is read.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.build( null, logFile ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "configuration must not be null" );
    }

    /**
     * Verifies that a null log file is rejected, since every launch must have somewhere to write.
     */
    @Test
    void shouldThrowException_whenLogFileIsNull() {
        // GIVEN
        // no log file

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.build( new LaunchConfiguration( minimalProfile(), password ), null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "logFile must not be null" );
    }

    /**
     * Verifies that the service cannot be created without the panel settings.
     */
    @Test
    void shouldThrowException_whenSystemPropertiesServiceIsNull() {
        // GIVEN
        // no settings service

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchCommandServiceImpl( null, pathService ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService must not be null" );
    }

    /**
     * Verifies that the service cannot be created without path resolution.
     */
    @Test
    void shouldThrowException_whenPathServiceIsNull() {
        // GIVEN
        // no path service

        // WHEN
        Throwable thrown = catchThrowable(
                () -> new LaunchCommandServiceImpl( systemPropertiesService, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "pathService must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Sets the password captured together with the profile.
     *
     * @param password
     *         password included in the configuration
     */
    private void givenPassword( String password ) {
        this.password = password;
    }

    /**
     * Points the panel at the publisher's start script by name and at the installation directory.
     */
    private void givenConfiguredStartScriptName() {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.SERVER_START_SCRIPT ) )
                .willReturn( SCRIPT_NAME );
        given( pathService.getBaseDirectory() ).willReturn( installationDirectory );
    }

    /**
     * Writes a publisher start script declaring a Steam application identifier.
     *
     * @param steamAppId
     *         identifier the script sets
     */
    private void givenStartScriptDeclaring( String steamAppId ) {
        givenConfiguredStartScriptName();
        try {
            Files.writeString( installationDirectory.resolve( SCRIPT_NAME ),
                    "@echo off\r\nset SteamAppId=" + steamAppId + "\r\nvalheim_server\r\n",
                    StandardCharsets.ISO_8859_1 );
        } catch ( IOException exception ) {
            throw new IllegalStateException( "Could not prepare the publisher start script", exception );
        }
    }

    /**
     * Resolves the world directory the way the path service would.
     */
    private void givenResolvedSaveDirectory() {
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) ).willReturn( savePath() );
    }

    private Path savePath() {
        return installationDirectory.resolve( SAVE_DIRECTORY );
    }

    /**
     * Builds a profile that exercises every optional setting.
     *
     * @return profile with a preset, two modifiers and two world keys
     */
    private static LaunchProfile fullProfile() {
        Map< WorldModifier, String > modifiers = new EnumMap<>( WorldModifier.class );
        modifiers.put( WorldModifier.COMBAT, "hard" );
        modifiers.put( WorldModifier.DEATH_PENALTY, "casual" );
        Set< WorldKey > keys = EnumSet.of( WorldKey.NO_BUILD_COST, WorldKey.NO_MAP );

        return new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, true, "sevenseas",
                SAVE_DIRECTORY, 1800, 4, 7200, 43200, WorldPreset.values()[ 0 ], modifiers, keys );
    }

    /**
     * Builds a profile in which every optional setting is left empty.
     *
     * @return profile using only the mandatory settings
     */
    private static LaunchProfile minimalProfile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", false, false, false, "",
                SAVE_DIRECTORY, 1800, 4, 7200, 43200, null, Map.of(), Set.of() );
    }
}
