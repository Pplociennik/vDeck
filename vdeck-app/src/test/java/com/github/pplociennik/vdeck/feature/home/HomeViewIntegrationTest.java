package com.github.pplociennik.vdeck.feature.home;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies that the panel home page is rendered by the real Thymeleaf engine together with the action
 * island fragment and the configured message source, so a broken fragment reference or an unresolved
 * translation key is caught by the build instead of by the running panel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class HomeViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Prefix of every translation key of the action island. An unresolved key is rendered as the key
     * itself, so its absence in the response proves that the texts came from the message source.
     */
    private static final String ISLAND_TRANSLATION_KEY_PREFIX = "island.";

    /**
     * Prefix of every translation key of the side menu, checked the same way as the island keys.
     */
    private static final String SIDE_MENU_TRANSLATION_KEY_PREFIX = "sideMenu.";

    /**
     * Prefix of every translation key of the home button, checked the same way as the island keys.
     */
    private static final String HOME_BUTTON_TRANSLATION_KEY_PREFIX = "homeButton.";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the panel root renders the home template with the island embedded and its actions
     * wired: the password-change link and the logout form Spring Security handles.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderHomePageWithIsland_whenAdministratorOpensPanelRoot() throws Exception {
        // GIVEN
        // an authenticated administrator opening the panel root

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "home/index" ) )
                .andExpect( content().string( not( containsString( ISLAND_TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( containsString( "class=\"island surface surface--island\"" ) ) )
                .andExpect( content().string( containsString( "<span class=\"island-name__label\">vDeck</span>" ) ) )
                .andExpect( content().string( containsString( "href=\"/change-password\"" ) ) )
                .andExpect( content().string( containsString( "action=\"/logout\"" ) ) );
    }

    /**
     * Verifies that the panel root embeds the side menu closed: its texts resolved, its entries grouped
     * into titled sections, the toggle collapsed, the panel inert, the menu icon referenced from the
     * sprite, the server and game settings entries linked but not marked as the current page, and its behaviour
     * module loaded.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderClosedSideMenu_whenAdministratorOpensPanelRoot() throws Exception {
        // GIVEN
        // an authenticated administrator opening the panel root

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( not( containsString( SIDE_MENU_TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( containsString( "data-side-menu-toggle" ) ) )
                .andExpect( content().string( containsString( "aria-controls=\"side-menu-panel\"" ) ) )
                .andExpect( content().string( containsString(
                        "<h2 class=\"side-menu-section__title\" id=\"side-menu-section-title-configuration\">" ) ) )
                .andExpect( content().string( containsString(
                        "class=\"side-menu-panel\" data-state=\"closed\" data-side-menu-panel inert" ) ) )
                .andExpect( content().string( containsString( "/icons/icons.svg#icon-menu" ) ) )
                .andExpect( content().string( containsString( "href=\"/server-settings\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/game-settings\"" ) ) )
                .andExpect( content().string( not( containsString( "aria-current" ) ) ) )
                .andExpect( content().string( containsString(
                        "<script type=\"module\" src=\"/js/side-menu.js\"></script>" ) ) );
    }

    /**
     * Verifies that the panel root renders the home button as a separate round link to the home page,
     * outside the side menu, with its accessible name translated and the house icon referenced from the
     * sprite.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderHomeButton_whenAdministratorOpensPanelRoot() throws Exception {
        // GIVEN
        // an authenticated administrator opening the panel root

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( not( containsString( HOME_BUTTON_TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( containsString(
                        "<a class=\"home-button round-button\" href=\"/\"" ) ) )
                .andExpect( content().string( containsString( "/icons/icons.svg#icon-home" ) ) )
                .andExpect( content().string( containsString( "class=\"side-menu-toggle round-button\"" ) ) );
    }

    /**
     * Verifies that the home page is not served to an anonymous visitor.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToLogin_whenPanelRootIsRequestedAnonymously() throws Exception {
        // GIVEN
        // no authenticated session

        // WHEN
        var result = underTest.perform( get( "/" ) );

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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "home-view-test.db" ) );
    }
}
