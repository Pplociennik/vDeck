package com.github.pplociennik.vdeck.feature.players;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
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
 * Verifies the player list panels end to end: rendering by the real Thymeleaf engine with resolved texts,
 * the marked menu entry and the restart warning, adding and removing a player in the list file of the
 * default save directory, rejection of an invalid id, CSRF and access control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class PlayerListViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Prefix of every translation key of the panels as it would start an element's text. An unresolved key
     * is rendered as the key itself, so its absence proves that the texts came from the message source.
     */
    private static final String PLAYERS_KEY_AS_TEXT = ">players.";

    /**
     * The same prefix as it would start an attribute value, such as a removal button's accessible name.
     */
    private static final String PLAYERS_KEY_AS_ATTRIBUTE = "\"players.";

    /**
     * Save directory of the default launch profile, relative to the installation directory.
     */
    private static final String SAVE_DIRECTORY = "valheim-data";

    private static final String STEAM_ID = "76561198012345678";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every panel renders with its texts resolved, the restart warning, the empty list and
     * its entry of the "Gracze" branch marked as the current page.
     *
     * @param segment
     *         address segment of the list
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @ParameterizedTest( name = "[{index}] list={0}" )
    @ValueSource( strings = { "bans", "admins", "permitted" } )
    void shouldRenderPanelWithWarningAndMarkedEntry_whenAdministratorOpensList( String segment ) throws Exception {
        // GIVEN
        // an authenticated administrator and lists without files

        // WHEN
        var result = underTest.perform( get( "/players/" + segment ).with( administrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "players/player-list" ) )
                .andExpect( content().string( not( containsString( PLAYERS_KEY_AS_TEXT ) ) ) )
                .andExpect( content().string( not( containsString( PLAYERS_KEY_AS_ATTRIBUTE ) ) ) )
                .andExpect( content().string( not( containsString( "sideMenu." ) ) ) )
                .andExpect( content().string( containsString( "class=\"form-warning\"" ) ) )
                .andExpect( content().string( containsString( "class=\"player-list-empty\"" ) ) )
                .andExpect( content().string( containsString( "action=\"/players/" + segment + "/add\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/css/players.css\"" ) ) )
                .andExpect( content().string( matchesPattern(
                        "(?s).*href=\"/players/" + segment + "\"\\s+aria-current=\"page\".*" ) ) );
    }

    /**
     * Verifies that an added player is written with the platform prefix to the list file of the save
     * directory and shown in the table, and that removing them empties the list again.
     *
     * @throws Exception
     *         if MockMvc cannot execute a request or the file cannot be read
     */
    @Test
    void shouldWriteAndRemoveEntry_whenPlayerIsAddedAndRemoved() throws Exception {
        // GIVEN
        Path file = directory.resolve( SAVE_DIRECTORY ).resolve( "bannedlist.txt" );

        // WHEN
        var added = underTest.perform( post( "/players/bans/add" ).with( administrator() ).with( csrf() )
                .param( "platform", "STEAM" )
                .param( "userId", STEAM_ID ) );

        // THEN
        added.andExpect( redirectedUrl( "/players/bans?added" ) );
        assertThat( Files.readAllLines( file ) ).containsExactly( "Steam_" + STEAM_ID );
        underTest.perform( get( "/players/bans" ).with( administrator() ) )
                .andExpect( content().string( containsString( ">" + STEAM_ID + "</td>" ) ) )
                .andExpect( content().string( containsString( "value=\"Steam_" + STEAM_ID + "\"" ) ) );

        underTest.perform( post( "/players/bans/remove" ).with( administrator() ).with( csrf() )
                        .param( "identifier", "Steam_" + STEAM_ID ) )
                .andExpect( redirectedUrl( "/players/bans?removed" ) );
        assertThat( Files.readAllLines( file ) ).isEmpty();
    }

    /**
     * Verifies that an id not fitting the chosen platform is rejected back to the panel, which then shows
     * an alert with the submitted id, while no list file is written.
     *
     * @throws Exception
     *         if MockMvc cannot execute a request
     */
    @Test
    void shouldRejectAndKeepFile_whenUserIdDoesNotFitPlatform() throws Exception {
        // GIVEN
        Path file = directory.resolve( SAVE_DIRECTORY ).resolve( "adminlist.txt" );

        // WHEN
        MvcResult rejected = underTest.perform( post( "/players/admins/add" ).with( administrator() ).with( csrf() )
                .param( "platform", "STEAM" )
                .param( "userId", "12345" ) ).andReturn();

        // THEN
        assertThat( rejected.getResponse().getRedirectedUrl() ).isEqualTo( "/players/admins" );
        Map< String, Object > flashed = rejected.getFlashMap();
        assertThat( flashed )
                .containsEntry( "playerListErrorKey", PlayerListMessage.INVALID_USER_ID.getKey() )
                .containsKey( "playerEntryForm" );
        underTest.perform( get( "/players/admins" ).with( administrator() ).flashAttrs( flashed ) )
                .andExpect( status().isOk() )
                .andExpect( content().string( containsString( "class=\"form-alert" ) ) )
                .andExpect( content().string( containsString( "value=\"12345\"" ) ) );
        assertThat( file ).doesNotExist();
    }

    /**
     * Verifies that changes without a CSRF token are refused.
     *
     * @throws Exception
     *         if MockMvc cannot execute a request
     */
    @Test
    void shouldRefuseChange_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        // requests carrying no CSRF token

        // WHEN
        var add = underTest.perform( post( "/players/permitted/add" ).with( administrator() )
                .param( "platform", "STEAM" )
                .param( "userId", STEAM_ID ) );
        var remove = underTest.perform( post( "/players/permitted/remove" ).with( administrator() )
                .param( "identifier", "Steam_" + STEAM_ID ) );

        // THEN
        add.andExpect( status().isForbidden() );
        remove.andExpect( status().isForbidden() );
    }

    /**
     * Verifies that an address naming no list is not found.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldAnswerNotFound_whenListIsUnknown() throws Exception {
        // GIVEN
        // an address segment no list carries

        // WHEN
        var result = underTest.perform( get( "/players/everyone" ).with( administrator() ) );

        // THEN
        result.andExpect( status().isNotFound() );
    }

    /**
     * Verifies that the panels are not served to an anonymous visitor.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToLogin_whenPanelIsRequestedAnonymously() throws Exception {
        // GIVEN
        // no authenticated session

        // WHEN
        var result = underTest.perform( get( "/players/bans" ) );

        // THEN
        result.andExpect( status().is3xxRedirection() );
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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "players-view-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }

    private static RequestPostProcessor administrator() {
        return user( "admin" ).roles( "ADMIN" );
    }
}
