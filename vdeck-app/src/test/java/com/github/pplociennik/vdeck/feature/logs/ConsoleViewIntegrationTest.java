package com.github.pplociennik.vdeck.feature.logs;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies that the console screen is rendered by the real Thymeleaf engine with its texts resolved and
 * its menu entry marked, and that the polled log endpoint answers with JSON only to the administrator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ConsoleViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Prefix of every translation key of the console screen as it would start an element's text. An
     * unresolved key is rendered as the key itself, so its absence in the response proves that the texts
     * came from the message source; the bare prefix also occurs in the script and stylesheet paths.
     */
    private static final String CONSOLE_KEY_AS_TEXT = ">console.";

    /**
     * The same prefix as it would start an attribute value, such as the output area's accessible name.
     */
    private static final String CONSOLE_KEY_AS_ATTRIBUTE = "\"console.";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the console renders its output area wired to the log endpoint, loads its script and
     * stylesheet, and marks the console entry of the "Procesy" menu section as the current page.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderConsoleWithMarkedMenuEntry_whenAdministratorOpensConsole() throws Exception {
        // GIVEN
        // an authenticated administrator opening the console

        // WHEN
        var result = underTest.perform( get( "/console" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "logs/console" ) )
                .andExpect( content().string( not( containsString( CONSOLE_KEY_AS_TEXT ) ) ) )
                .andExpect( content().string( not( containsString( CONSOLE_KEY_AS_ATTRIBUTE ) ) ) )
                .andExpect( content().string( not( containsString( "sideMenu." ) ) ) )
                .andExpect( content().string( containsString( "data-log-url=\"/console/log\"" ) ) )
                .andExpect( content().string( containsString( "<script type=\"module\" src=\"/js/console.js\"></script>" ) ) )
                .andExpect( content().string( containsString( "href=\"/css/console.css\"" ) ) )
                .andExpect( content().string( containsString( "id=\"side-menu-section-title-processes\"" ) ) )
                .andExpect( content().string( containsString( "href=\"/console\"" ) ) )
                .andExpect( content().string( containsString( "aria-current=\"page\"" ) ) );
    }

    /**
     * Verifies that a poll without a launch log answers with an empty JSON chunk telling the browser to
     * keep the console empty.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldReturnEmptyChunk_whenLogIsPolledWithoutLaunch() throws Exception {
        // GIVEN
        // a panel that has not launched the server

        // WHEN
        var result = underTest.perform( get( "/console/log" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( jsonPath( "$.source" ).doesNotExist() )
                .andExpect( jsonPath( "$.text" ).value( "" ) )
                .andExpect( jsonPath( "$.reset" ).value( true ) );
    }

    /**
     * Verifies that the log endpoint is not served to an anonymous visitor.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToLogin_whenLogIsPolledAnonymously() throws Exception {
        // GIVEN
        // no authenticated session

        // WHEN
        var result = underTest.perform( get( "/console/log" ) );

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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "console-view-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }
}
