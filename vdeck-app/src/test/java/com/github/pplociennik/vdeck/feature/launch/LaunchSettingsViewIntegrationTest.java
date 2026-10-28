package com.github.pplociennik.vdeck.feature.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies the launch profile panels - the four panels of the "Serwer" branch and "Ustawienia gry" - end
 * to end: rendering by the real Thymeleaf engine with the message source, storing each panel's settings in
 * {@code .vDeck/config/valheim-server.properties} without touching the other panels' settings,
 * rejecting an invalid submission without writing, keeping the stored server password out of every page,
 * and remembering the visibility of the advanced game settings in {@code vDeck.properties}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class LaunchSettingsViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String SERVER_PASSWORD = "Tr0ll-Hunter";

    private static final String HIDDEN_ADVANCED_SECTION = "data-advanced-section hidden=\"hidden\"";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every panel of the "Serwer" branch renders only its own fields with resolved texts,
     * posts to its own address, and shows the side menu with all four panels and its own entry marked as
     * the current page.
     *
     * @param panel
     *         panel name in the address
     * @param ownField
     *         name of a field the panel shows
     * @param otherField
     *         name of a field belonging to another panel
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @ParameterizedTest( name = "[{index}] panel={0}" )
    @CsvSource( { "server, serverName, worldName", "access, password, backupCount",
            "world, saveDirectory, port", "backups, backupLong, serverName" } )
    void shouldRenderOnlyPanelFields_whenAdministratorOpensServerPanel( String panel, String ownField,
                                                                        String otherField ) throws Exception {
        // GIVEN
        String path = "/server-settings/" + panel;

        // WHEN
        var result = underTest.perform( get( path ).with( administrator() ) );

        // THEN
        String page = result.andReturn().getResponse().getContentAsString( StandardCharsets.UTF_8 );
        assertThat( page ).containsPattern( "href=\"" + path + "\"\\s+aria-current=\"page\"" );
        result.andExpect( status().isOk() )
                .andExpect( view().name( "launch/server-settings" ) )
                .andExpect( content().string( not( containsString( "launchProfile." ) ) ) )
                .andExpect( content().string( not( containsString( "serverSettings." ) ) ) )
                .andExpect( content().string( not( containsString( "sideMenu." ) ) ) )
                .andExpect( content().string( containsString( "name=\"" + ownField + "\"" ) ) )
                .andExpect( content().string( not( containsString( "name=\"" + otherField + "\"" ) ) ) )
                .andExpect( content().string( not( containsString( "name=\"preset\"" ) ) ) )
                .andExpect( content().string( containsString( "action=\"" + path + "\"" ) ) )
                .andExpect( content().string( containsString( "aria-controls=\"side-menu-children-server-settings\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings/server\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings/access\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings/world\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings/backups\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/game-settings\"" ) ) );
    }

    /**
     * Verifies that the "Ustawienia gry" screen renders the preset, the toggle and the advanced section
     * with resolved texts, and none of the server settings.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderGameSettingsScreen_whenAdministratorOpensIt() throws Exception {
        // GIVEN
        // an authenticated administrator

        // WHEN
        var result = underTest.perform( get( "/game-settings" ).with( administrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "launch/game-settings" ) )
                .andExpect( content().string( not( containsString( "launchProfile." ) ) ) )
                .andExpect( content().string( not( containsString( "gameSettings." ) ) ) )
                .andExpect( content().string( containsString( "<option value=\"hammer\"" ) ) )
                .andExpect( content().string( containsString( "class=\"preset-guide__list\"" ) ) )
                .andExpect( content().string( containsString( "name=\"deathPenaltyModifier\"" ) ) )
                .andExpect( content().string( containsString( "name=\"noMap\"" ) ) )
                .andExpect( content().string( containsString( "data-advanced-toggle" ) ) )
                .andExpect( content().string( containsString( "data-advanced-url=\"/game-settings/advanced\"" ) ) )
                .andExpect( content().string( not( containsString( "name=\"serverName\"" ) ) ) );
    }

    /**
     * Verifies that a valid server identity is written to the launch profile file in the configuration
     * directory, leaves the settings of the other panels untouched and redirects back with the
     * confirmation flag.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldStoreOnlyIdentitySettings_whenIdentitySubmissionIsValid() throws Exception {
        // GIVEN
        underTest.perform( gameSubmission( "none" ) ).andExpect( status().is3xxRedirection() );
        underTest.perform( worldSubmission( "Midgard" ) ).andExpect( status().is3xxRedirection() );
        MockHttpServletRequestBuilder request = identitySubmission( "2600", "vDeck Midgard" );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/server-settings/server?saved" ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) )
                .containsEntry( "server.port", "2600" )
                .containsEntry( "server.name", "vDeck Midgard" )
                .containsEntry( "server.world", "Midgard" )
                .containsEntry( "world.modifier.raids", "none" )
                .containsEntry( "world.key.no.map", "true" );
        assertThat( directory.resolve( ".vDeck/config/vDeck.properties" ) ).isRegularFile();
        assertThat( directory.resolve( "vDeck.properties" ) ).doesNotExist();
    }

    /**
     * Verifies that valid backup settings are written to the launch profile file and redirect back to the
     * "Kopie automatyczne" panel with the confirmation flag.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldStoreBackupSettings_whenBackupsSubmissionIsValid() throws Exception {
        // GIVEN
        MockHttpServletRequestBuilder request = post( "/server-settings/backups" ).with( administrator() )
                .with( csrf() )
                .param( "backupCount", "6" )
                .param( "backupShort", "3600" )
                .param( "backupLong", "86400" );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/server-settings/backups?saved" ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) )
                .containsEntry( "server.backup.count", "6" )
                .containsEntry( "server.backup.short", "3600" )
                .containsEntry( "server.backup.long", "86400" );
    }

    /**
     * Verifies that valid game settings are written to the launch profile file, leave the server settings
     * untouched and redirect back with the confirmation flag.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldStoreOnlyGameSettings_whenGameSubmissionIsValid() throws Exception {
        // GIVEN
        underTest.perform( identitySubmission( "2700", "vDeck Asgard" ) ).andExpect( status().is3xxRedirection() );
        underTest.perform( worldSubmission( "Asgard" ) ).andExpect( status().is3xxRedirection() );
        MockHttpServletRequestBuilder request = gameSubmission( "more" );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/game-settings?saved" ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) )
                .containsEntry( "world.modifier.raids", "more" )
                .containsEntry( "world.modifier.death.penalty", "hardcore" )
                .containsEntry( "world.key.passive.mobs", "false" )
                .containsEntry( "server.port", "2700" )
                .containsEntry( "server.world", "Asgard" );
    }

    /**
     * Verifies that the stored server password never appears in the rendered "Dostęp" panel; only the
     * fact that one is set is shown.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldNotRenderStoredPassword_whenAccessPanelIsOpenedAfterSaving() throws Exception {
        // GIVEN
        underTest.perform( accessSubmission( SERVER_PASSWORD ) )
                .andExpect( redirectedUrl( "/server-settings/access?saved" ) );

        // WHEN
        var result = underTest.perform( get( "/server-settings/access" ).queryParam( "saved", "" )
                .with( administrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( not( containsString( SERVER_PASSWORD ) ) ) )
                .andExpect( content().string( containsString( "name=\"clearPassword\"" ) ) )
                .andExpect( content().string( containsString( "class=\"form-success" ) ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) )
                .containsEntry( "server.password", SERVER_PASSWORD );
    }

    /**
     * Verifies that an out-of-range value is rejected by redirecting back to the panel, which then shows
     * an alert with the submitted edits while the stored profile is untouched.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldRejectSubmission_whenPortIsOutOfRange() throws Exception {
        // GIVEN
        underTest.perform( identitySubmission( "2456", "vDeck Midgard" ) ).andExpect( status().is3xxRedirection() );
        Properties before = readConfigurationFile( "valheim-server.properties" );
        MockHttpServletRequestBuilder request = identitySubmission( "80", "Other server" );

        // WHEN
        MvcResult rejected = underTest.perform( request ).andReturn();

        // THEN
        assertThat( rejected.getResponse().getRedirectedUrl() ).isEqualTo( "/server-settings/server" );
        Map< String, Object > flashed = rejected.getFlashMap();
        assertThat( flashed )
                .containsEntry( "launchProfileErrorKey", "launchProfile.error.invalidInput" )
                .containsKey( "serverIdentityForm" );
        underTest.perform( get( "/server-settings/server" ).with( administrator() ).flashAttrs( flashed ) )
                .andExpect( status().isOk() )
                .andExpect( content().string( containsString( "class=\"form-alert" ) ) )
                .andExpect( content().string( containsString( "value=\"Other server\"" ) ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) ).isEqualTo( before );
    }

    /**
     * Verifies that a password breaking the format rules is rejected and shown again on the "Dostęp"
     * panel without the typed password, while the stored profile is untouched.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldRejectSubmissionWithoutEchoingPassword_whenPasswordIsInvalid() throws Exception {
        // GIVEN
        Properties before = readConfigurationFile( "valheim-server.properties" );
        String invalidPassword = "Bad\"Secret";

        // WHEN
        MvcResult rejected = underTest.perform( accessSubmission( invalidPassword ) ).andReturn();

        // THEN
        assertThat( rejected.getResponse().getRedirectedUrl() ).isEqualTo( "/server-settings/access" );
        Map< String, Object > flashed = rejected.getFlashMap();
        assertThat( flashed ).containsKey( "serverAccessForm" );
        underTest.perform( get( "/server-settings/access" ).with( administrator() ).flashAttrs( flashed ) )
                .andExpect( status().isOk() )
                .andExpect( content().string( containsString( "class=\"form-alert" ) ) )
                .andExpect( content().string( not( containsString( "Bad&quot;Secret" ) ) ) )
                .andExpect( content().string( not( containsString( invalidPassword ) ) ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) ).isEqualTo( before );
    }

    /**
     * Verifies that the advanced section follows the remembered choice: shown after it is switched on,
     * with the choice stored in the panel's settings file, and hidden again after it is switched off.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldRememberAdvancedVisibility_whenToggled() throws Exception {
        // GIVEN
        MockHttpServletRequestBuilder show = advancedToggle( "true" );

        // WHEN
        var result = underTest.perform( show );

        // THEN
        result.andExpect( status().isNoContent() );
        assertThat( readConfigurationFile( "vDeck.properties" ) ).containsEntry( "panel.game.settings.advanced", "true" );
        underTest.perform( get( "/game-settings" ).with( administrator() ) )
                .andExpect( content().string( not( containsString( HIDDEN_ADVANCED_SECTION ) ) ) );

        underTest.perform( advancedToggle( "false" ) ).andExpect( status().isNoContent() );
        assertThat( readConfigurationFile( "vDeck.properties" ) ).containsEntry( "panel.game.settings.advanced", "false" );
        underTest.perform( get( "/game-settings" ).with( administrator() ) )
                .andExpect( content().string( containsString( HIDDEN_ADVANCED_SECTION ) ) );
    }

    /**
     * Verifies that submissions without a CSRF token are refused on both screens and on the toggle.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRefuseSubmission_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        // requests carrying no CSRF token

        // WHEN
        var server = underTest.perform( post( "/server-settings/access" ).with( administrator() )
                .param( "password", SERVER_PASSWORD ) );
        var game = underTest.perform( post( "/game-settings" ).with( administrator() ).param( "preset", "hard" ) );
        var toggle = underTest.perform( post( "/game-settings/advanced" ).with( administrator() ).param( "shown", "true" ) );

        // THEN
        server.andExpect( status().isForbidden() );
        game.andExpect( status().isForbidden() );
        toggle.andExpect( status().isForbidden() );
    }

    /**
     * Verifies that the screens are not served to an anonymous visitor.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToLogin_whenScreenIsRequestedAnonymously() throws Exception {
        // GIVEN
        // no authenticated session

        // WHEN
        var server = underTest.perform( get( "/server-settings/world" ) );
        var game = underTest.perform( get( "/game-settings" ) );

        // THEN
        server.andExpect( status().is3xxRedirection() );
        game.andExpect( status().is3xxRedirection() );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Supplies an isolated installation directory and SQLite file before the Spring context starts.
     *
     * @param registry
     *         dynamic application property registry
     */
    @DynamicPropertySource
    static void registerApplicationProperties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "launch-view-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }

    private static RequestPostProcessor administrator() {
        return user( "admin" ).roles( "ADMIN" );
    }

    private static MockHttpServletRequestBuilder identitySubmission( String port, String serverName ) {
        return post( "/server-settings/server" ).with( administrator() ).with( csrf() )
                .param( "serverName", serverName )
                .param( "port", port )
                .param( "publicServer", "true" )
                .param( "instanceId", "" );
    }

    private static MockHttpServletRequestBuilder accessSubmission( String password ) {
        return post( "/server-settings/access" ).with( administrator() ).with( csrf() )
                .param( "password", password );
    }

    private static MockHttpServletRequestBuilder worldSubmission( String worldName ) {
        return post( "/server-settings/world" ).with( administrator() ).with( csrf() )
                .param( "worldName", worldName )
                .param( "saveDirectory", "valheim-data" )
                .param( "saveInterval", "1800" );
    }

    private static MockHttpServletRequestBuilder gameSubmission( String raids ) {
        return post( "/game-settings" ).with( administrator() ).with( csrf() )
                .param( "preset", "" )
                .param( "combatModifier", "" )
                .param( "deathPenaltyModifier", "hardcore" )
                .param( "resourcesModifier", "" )
                .param( "raidsModifier", raids )
                .param( "portalsModifier", "" )
                .param( "noMap", "true" );
    }

    private static MockHttpServletRequestBuilder advancedToggle( String shown ) {
        return post( "/game-settings/advanced" ).with( administrator() ).with( csrf() ).param( "shown", shown );
    }

    private static Properties readConfigurationFile( String fileName ) throws IOException {
        Properties properties = new Properties();
        try ( Reader reader = Files.newBufferedReader( directory.resolve( ".vDeck/config" ).resolve( fileName ),
                StandardCharsets.UTF_8 ) ) {
            properties.load( reader );
        }
        return properties;
    }
}
