package com.github.pplociennik.vdeck.feature.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * Verifies the two launch profile screens, "Serwer" and "Ustawienia gry", end to end: rendering by the
 * real Thymeleaf engine with the message source, storing each screen's settings in
 * {@code .vDeck/config/valheim-server.properties} without touching the other screen's settings,
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

    private static final String HIDDEN_ATTRIBUTE = "hidden=\"hidden\"";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the "Serwer" screen renders its four sections with resolved texts, none of the game
     * settings, and the side menu with its entry marked as the current page.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderServerSettingsScreen_whenAdministratorOpensIt() throws Exception {
        // GIVEN
        // an authenticated administrator

        // WHEN
        var result = underTest.perform( get( "/server-settings" ).with( administrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "launch/server-settings" ) )
                .andExpect( content().string( not( containsString( "launchProfile." ) ) ) )
                .andExpect( content().string( not( containsString( "serverSettings." ) ) ) )
                .andExpect( content().string( containsString( "name=\"serverName\"" ) ) )
                .andExpect( content().string( containsString( "name=\"backupLong\"" ) ) )
                .andExpect( content().string( not( containsString( "name=\"preset\"" ) ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/game-settings\"" ) ) )
                .andExpect( content().string( containsString( "aria-current=\"page\"" ) ) );
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
                .andExpect( content().string( containsString( "name=\"deathPenaltyModifier\"" ) ) )
                .andExpect( content().string( containsString( "name=\"noMap\"" ) ) )
                .andExpect( content().string( containsString( "data-advanced-toggle" ) ) )
                .andExpect( content().string( containsString( "data-advanced-url=\"/game-settings/advanced\"" ) ) )
                .andExpect( content().string( not( containsString( "name=\"serverName\"" ) ) ) );
    }

    /**
     * Verifies that valid server settings are written to the launch profile file in the configuration
     * directory, leave the game settings untouched and redirect back with the confirmation flag.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldStoreOnlyServerSettings_whenServerSubmissionIsValid() throws Exception {
        // GIVEN
        underTest.perform( gameSubmission( "none" ) ).andExpect( status().is3xxRedirection() );
        MockHttpServletRequestBuilder request = serverSubmission( "2600", "Midgard" );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/server-settings?saved" ) );
        assertThat( readConfigurationFile( "valheim-server.properties" ) )
                .containsEntry( "server.port", "2600" )
                .containsEntry( "server.world", "Midgard" )
                .containsEntry( "world.modifier.raids", "none" )
                .containsEntry( "world.key.no.map", "true" );
        assertThat( directory.resolve( ".vDeck/config/vDeck.properties" ) ).isRegularFile();
        assertThat( directory.resolve( "vDeck.properties" ) ).doesNotExist();
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
        underTest.perform( serverSubmission( "2700", "Asgard" ) ).andExpect( status().is3xxRedirection() );
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
     * Verifies that the stored server password never appears in the rendered screen; only the fact that
     * one is set is shown.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldNotRenderStoredPassword_whenScreenIsOpenedAfterSaving() throws Exception {
        // GIVEN
        underTest.perform( serverSubmission( "2456", "Midgard" ) ).andExpect( status().is3xxRedirection() );

        // WHEN
        var result = underTest.perform( get( "/server-settings" ).queryParam( "saved", "" ).with( administrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( not( containsString( SERVER_PASSWORD ) ) ) )
                .andExpect( content().string( containsString( "name=\"clearPassword\"" ) ) )
                .andExpect( content().string( containsString( "class=\"form-success" ) ) );
    }

    /**
     * Verifies that an out-of-range value is rejected by redirecting back to the screen, which then
     * shows an alert with the submitted edits, never the password, while the stored profile is untouched.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request or the file cannot be read
     */
    @Test
    void shouldRejectSubmission_whenPortIsOutOfRange() throws Exception {
        // GIVEN
        underTest.perform( serverSubmission( "2456", "Midgard" ) ).andExpect( status().is3xxRedirection() );
        Properties before = readConfigurationFile( "valheim-server.properties" );
        MockHttpServletRequestBuilder request = serverSubmission( "80", "Other" );

        // WHEN
        MvcResult rejected = underTest.perform( request ).andReturn();

        // THEN
        assertThat( rejected.getResponse().getRedirectedUrl() ).isEqualTo( "/server-settings" );
        Map< String, Object > flashed = rejected.getFlashMap();
        assertThat( flashed )
                .containsEntry( "launchProfileErrorKey", "launchProfile.error.invalidInput" )
                .containsKey( "serverSettingsForm" );
        underTest.perform( get( "/server-settings" ).with( administrator() ).flashAttrs( rejected.getFlashMap() ) )
                .andExpect( status().isOk() )
                .andExpect( content().string( containsString( "class=\"form-alert" ) ) )
                .andExpect( content().string( containsString( "value=\"Other\"" ) ) )
                .andExpect( content().string( not( containsString( SERVER_PASSWORD ) ) ) );
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
                .andExpect( content().string( not( containsString( HIDDEN_ATTRIBUTE ) ) ) );

        underTest.perform( advancedToggle( "false" ) ).andExpect( status().isNoContent() );
        assertThat( readConfigurationFile( "vDeck.properties" ) ).containsEntry( "panel.game.settings.advanced", "false" );
        underTest.perform( get( "/game-settings" ).with( administrator() ) )
                .andExpect( content().string( containsString( HIDDEN_ATTRIBUTE ) ) );
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
        var server = underTest.perform( post( "/server-settings" ).with( administrator() ).param( "serverName", "vDeck" ) );
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
        var server = underTest.perform( get( "/server-settings" ) );
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

    private static MockHttpServletRequestBuilder serverSubmission( String port, String worldName ) {
        return post( "/server-settings" ).with( administrator() ).with( csrf() )
                .param( "serverName", "vDeck Midgard" )
                .param( "port", port )
                .param( "worldName", worldName )
                .param( "password", SERVER_PASSWORD )
                .param( "publicServer", "true" )
                .param( "instanceId", "" )
                .param( "saveDirectory", "valheim-data" )
                .param( "saveInterval", "1800" )
                .param( "backupCount", "4" )
                .param( "backupShort", "7200" )
                .param( "backupLong", "43200" );
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
