package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies that the server state summary is rendered by the real Thymeleaf engine from the default launch
 * profile, with its texts resolved, its links to the related panels and its side menu action marked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ServerStateViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    @MockitoBean
    private ServerService serverService;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a stopped server renders the summary of the default profile, links to every related
     * panel, loads the refresh script and marks the "Stan serwera" action of the side menu.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderSummaryWithLinksAndMarkedMenuAction_whenAdministratorOpensServerState() throws Exception {
        // GIVEN
        given( serverService.getStatus() ).willReturn( ServerStatus.stopped() );

        // WHEN
        var result = underTest.perform( get( "/server-state" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "server/server-state" ) )
                .andExpect( content().string( allOf(
                        not( containsString( ">serverState." ) ),
                        not( containsString( "\"serverState." ) ),
                        not( containsString( ">launchProfile." ) ),
                        not( containsString( ">server.state." ) ),
                        not( containsString( "sideMenu." ) ) ) ) )
                .andExpect( content().string( allOf(
                        containsString( "data-state-url=\"/server-state\"" ),
                        containsString( "<script type=\"module\" src=\"/js/server-state.js\"></script>" ),
                        containsString( "href=\"/css/server-state.css\"" ),
                        containsString( "2456–2457" ),
                        containsString( "Dedicated" ),
                        containsString( "valheim-data" ) ) ) )
                .andExpect( content().string( allOf(
                        containsString( "class=\"server-state-link\" href=\"/console\"" ),
                        containsString( "class=\"server-state-link\" href=\"/server-settings/server\"" ),
                        containsString( "class=\"server-state-link\" href=\"/server-settings/access\"" ),
                        containsString( "class=\"server-state-link\" href=\"/server-settings/world\"" ),
                        containsString( "class=\"server-state-link\" href=\"/server-settings/inner-server-backups\"" ),
                        containsString( "class=\"server-state-link\" href=\"/game-settings\"" ),
                        containsString( "class=\"side-menu-state\" href=\"/server-state\"" ),
                        containsString( "aria-current=\"page\"" ) ) ) );
    }

    /**
     * Verifies that a running server shows its process identifier, the log file name, the local start time
     * and the uptime formatted by the real template engine.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderProcessDetails_whenServerIsRunning() throws Exception {
        // GIVEN
        Instant startedAt = Instant.now().minus( Duration.ofDays( 2 ).plusHours( 3 ).plusMinutes( 4 ) );
        Path logFile = directory.resolve( "2026-10-29_12-00-00.log" ).toAbsolutePath();
        given( serverService.getStatus() ).willReturn( ServerStatus.running( 4242, startedAt, logFile ) );
        String startText = DateTimeFormatter.ofPattern( "dd.MM.yyyy HH:mm:ss" )
                .format( LocalDateTime.ofInstant( startedAt, ZoneId.systemDefault() ) );

        // WHEN
        var result = underTest.perform( get( "/server-state" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( allOf(
                        containsString( ">4242<" ),
                        containsString( ">2026-10-29_12-00-00.log<" ),
                        containsString( ">" + startText + "<" ),
                        containsString( ">2 d 3 h 4 min<" ),
                        containsString( "server-state-status is-running" ) ) ) );
    }

    /**
     * Verifies that the summary is not served to an anonymous visitor.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToLogin_whenServerStateIsOpenedAnonymously() throws Exception {
        // GIVEN
        // no authenticated session

        // WHEN
        var result = underTest.perform( get( "/server-state" ) );

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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "server-state-view-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }
}
