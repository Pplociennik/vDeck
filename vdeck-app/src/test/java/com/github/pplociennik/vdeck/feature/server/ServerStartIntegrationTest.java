package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Verifies real security, MVC, start orchestration and Thymeleaf rendering with a mocked runtime.
 * All files belong to an isolated installation; no server process is ever created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ServerStartIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    @Autowired
    private SystemPropertiesService properties;

    @Autowired
    private LaunchProfileService launchProfileService;

    @MockitoBean
    private ServerRuntime serverRuntime;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies authentication independently of CSRF by supplying a valid token.
     *
     * @throws Exception
     *         if request execution fails
     */
    @Test
    void shouldRedirectToLogin_whenAnonymousPostsStart() throws Exception {
        // GIVEN
        var request = post( "/server/start" ).with( csrf() );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().is3xxRedirection() ).andExpect( redirectedUrl( "/login" ) );
        verify( serverRuntime, never() ).start( any() );
    }

    /**
     * Verifies that an administrator still needs a CSRF token to start a process.
     *
     * @throws Exception
     *         if request execution fails
     */
    @Test
    void shouldRejectRequest_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        var request = post( "/server/start" ).with( user( "admin" ).roles( "ADMIN" ) );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().isForbidden() );
        verify( serverRuntime, never() ).start( any() );
    }

    /**
     * Verifies that a regular authenticated user cannot perform an administrator action.
     *
     * @throws Exception
     *         if request execution fails
     */
    @Test
    void shouldRejectRequest_whenUserIsNotAdministrator() throws Exception {
        // GIVEN
        var request = post( "/server/start" ).with( user( "visitor" ).roles( "USER" ) ).with( csrf() );

        // WHEN
        var result = underTest.perform( request );

        // THEN
        result.andExpect( status().isForbidden() );
        verify( serverRuntime, never() ).start( any() );
    }

    /**
     * Verifies the complete accepted HTTP flow, including real validation and command preparation.
     *
     * @throws Exception
     *         if temporary files or request execution fail
     */
    @Test
    void shouldRedirectWithConfirmation_whenStartAccepted() throws Exception {
        // GIVEN
        Files.writeString( directory.resolve( "valheim_server.exe" ), "stand-in; never executed" );
        properties.setValues( LaunchProperty.FILE_NAME, SystemPropertyValues.create()
                .with( LaunchProperty.SERVER_PUBLIC, "false" )
                .with( LaunchProperty.SERVER_PORT, Integer.toString( freePortPair() ) ) );
        launchProfileService.reload();
        given( serverRuntime.status() ).willReturn( ServerStatus.stopped() );
        given( serverRuntime.start( any() ) ).willReturn(
                ServerStatus.starting( 42L, Instant.EPOCH, directory.resolve( "run.log" ) ) );

        // WHEN
        var result = underTest.perform( post( "/server/start" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );

        // THEN
        result.andExpect( status().is3xxRedirection() ).andExpect( redirectedUrl( "/" ) )
                .andExpect( flash().attribute( "serverStarted", true ) );
        verify( serverRuntime ).start( any() );
        assertThat( directory.resolve( ".vDeck/vDeck-server-start.bat" ) ).isRegularFile();
    }

    /**
     * Verifies that a rejection travels through the feature advice and renders a translated alert.
     *
     * @throws Exception
     *         if request execution fails
     */
    @Test
    void shouldRedirectWithReason_whenStartRejected() throws Exception {
        // GIVEN
        given( serverRuntime.status() ).willReturn(
                ServerStatus.running( 42L, Instant.EPOCH, directory.resolve( "run.log" ) ) );

        // WHEN
        var result = underTest.perform( post( "/server/start" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );

        // THEN
        result.andExpect( redirectedUrl( "/" ) )
                .andExpect( flash().attribute( "serverErrorKey", ServerMessage.ALREADY_RUNNING.getKey() ) );
        var page = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) )
                .flashAttrs( result.andReturn().getFlashMap() ) ).andReturn().getResponse();
        assertThat( page.getStatus() ).isEqualTo( 200 );
        assertThat( page.getContentAsString() ).contains( "role=\"alert\"" )
                .doesNotContain( ServerMessage.ALREADY_RUNNING.getKey(), "server.state." );
        verify( serverRuntime, never() ).start( any() );
    }

    /**
     * Verifies the status badge is resolved by Thymeleaf and the actual Polish message source.
     *
     * @throws Exception
     *         if request execution fails
     */
    @Test
    void shouldRenderStatusBadge_whenHomePageIsOpened() throws Exception {
        // GIVEN
        given( serverRuntime.status() ).willReturn( ServerStatus.stopped() );

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() );
        assertThat( result.andReturn().getResponse().getContentAsString() )
                .contains( "island-status", "is-stopped" ).doesNotContain( "server.state." );
    }

    /**
     * Verifies that every start rejection and failure resolves to a message without exposing its key.
     *
     * @param message
     *         reason carried across a start redirect
     * @throws Exception
     *         if request execution fails
     */
    @ParameterizedTest( name = "[{index}] translated {0}" )
    @EnumSource( ServerMessage.class )
    void shouldRenderTranslatedAlert_whenStartReasonIsFlashed( ServerMessage message ) throws Exception {
        // GIVEN
        given( serverRuntime.status() ).willReturn( ServerStatus.stopped() );

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) )
                .flashAttr( "serverErrorKey", message.getKey() ) );

        // THEN
        result.andExpect( status().isOk() );
        assertThat( result.andReturn().getResponse().getContentAsString() )
                .contains( "role=\"alert\"" ).doesNotContain( message.getKey() );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Isolates the installation and database before the Spring context is created.
     *
     * @param registry
     *         property overrides for this test context
     */
    @DynamicPropertySource
    static void registerApplicationProperties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "server-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }

    private static int freePortPair() throws SocketException {
        for ( int attempt = 0; attempt < 100; attempt++ ) {
            try ( var first = new DatagramSocket( 0 ) ) {
                int port = first.getLocalPort();
                if ( port >= 65535 ) {
                    continue;
                }
                try ( var second = new DatagramSocket( port + 1 ) ) {
                    return second.getLocalPort() - 1;
                } catch ( SocketException occupied ) {
                    // Retry a different ephemeral pair if its neighbour is occupied.
                }
            }
        }
        throw new SocketException( "No free UDP port pair found" );
    }
}
