package com.github.pplociennik.vdeck.feature.shutdown;

import com.github.pplociennik.vdeck.feature.shutdown.service.PanelShutdownService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies the panel shutdown screen rendered by the real Thymeleaf engine, Spring Security and the message
 * source. The shutdown service is a mock: the real one would end the test process.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class PanelShutdownViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Prefix of every translation key of the screen; an unresolved key is rendered as the key itself.
     */
    private static final String TRANSLATION_KEY_PREFIX = "panelShutdown.";

    @TempDir
    private static Path directory;

    @MockitoBean
    private PanelShutdownService panelShutdownService;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the side menu offers the icon-only action opening the shutdown confirmation, named by its
     * translated accessible label, next to the "Admin" action that stays disabled for now.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderCloseAndDisabledAdminActionsInSideMenu_whenAdministratorOpensPanelRoot() throws Exception {
        // GIVEN
        // an authenticated administrator opening the panel root

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( containsString(
                        "<a class=\"side-menu-close\" href=\"/panel/shutdown\" aria-label=" ) ) )
                .andExpect( content().string( containsString(
                        "<button class=\"side-menu-admin\" type=\"button\" disabled>" ) ) )
                .andExpect( content().string( containsString( "/icons/icons.svg#icon-gear" ) ) )
                .andExpect( content().string( not( containsString( "sideMenu.action." ) ) ) );
    }

    /**
     * Verifies that the screen opens with the confirmation shown, the closing message hidden, a CSRF-protected
     * form and no closing started.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderConfirmationWithoutClosing_whenAdministratorOpensScreen() throws Exception {
        // GIVEN
        // an authenticated administrator opening the screen

        // WHEN
        var result = underTest.perform( get( "/panel/shutdown" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "shutdown/panel-shutdown" ) )
                .andExpect( content().string( not( containsString( TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( matchesRegex( shownSection( "data-panel-shutdown-confirmation" ) ) ) )
                .andExpect( content().string( matchesRegex( hiddenSection( "data-panel-shutdown-closing" ) ) ) )
                .andExpect( content().string( containsString( "name=\"_csrf\"" ) ) )
                .andExpect( content().string( containsString(
                        "<script type=\"module\" src=\"/js/panel-shutdown.js\">" ) ) );
        verify( panelShutdownService, never() ).requestShutdown();
    }

    /**
     * Verifies that the confirmed request closes the panel and answers with the closing message shown.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRequestShutdownAndRenderClosingMessage_whenAdministratorConfirms() throws Exception {
        // GIVEN
        // an authenticated administrator confirming with a CSRF token

        // WHEN
        var result = underTest.perform( post( "/panel/shutdown" )
                .with( csrf() )
                .with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( matchesRegex( hiddenSection( "data-panel-shutdown-confirmation" ) ) ) )
                .andExpect( content().string( matchesRegex( shownSection( "data-panel-shutdown-closing" ) ) ) );
        verify( panelShutdownService ).requestShutdown();
    }

    /**
     * Verifies that a closing request without a CSRF token is refused and closes nothing.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRefuseWithoutClosing_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        // an authenticated administrator posting without a CSRF token

        // WHEN
        var result = underTest.perform( post( "/panel/shutdown" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isForbidden() );
        verify( panelShutdownService, never() ).requestShutdown();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Builds a pattern matching a whole page whose section carrying the given attribute is hidden.
     *
     * @param marker
     *         data attribute identifying the section
     * @return the regular expression matching the whole rendered page
     */
    private static String hiddenSection( String marker ) {
        return "(?s).*<section " + marker + "[^>]*hidden=\"hidden\"[^>]*>.*";
    }

    /**
     * Builds a pattern matching a whole page whose section carrying the given attribute is shown.
     *
     * @param marker
     *         data attribute identifying the section
     * @return the regular expression matching the whole rendered page
     */
    private static String shownSection( String marker ) {
        return "(?s).*<section " + marker + "(?:(?!hidden)[^>])*>.*";
    }

    /**
     * Supplies an isolated installation directory and SQLite file before the Spring context starts.
     *
     * @param registry
     *         dynamic application property registry
     */
    @DynamicPropertySource
    static void registerApplicationProperties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "shutdown-view-test.db" ) );
    }
}
