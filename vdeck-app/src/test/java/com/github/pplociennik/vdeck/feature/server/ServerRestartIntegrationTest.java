package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** Verifies the island restart form and its MVC/Security contract without creating or signaling real processes. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ServerRestartIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    @TempDir
    private static Path directory;
    @Autowired
    private MockMvc underTest;
    @MockitoBean
    private ServerRuntime runtime;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Prepares a correlated running launch; only the runtime boundary is substituted. */
    @BeforeEach
    void setUp() {
        ProcessHandle handle = mock( ProcessHandle.class );
        given( handle.pid() ).willReturn( 42L );
        var running = ServerStatus.running(
                new RunningServer( handle, Instant.EPOCH, directory.resolve( "run.log" ) ), "current-launch" );
        given( runtime.status() ).willReturn( running );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /**
     * Anonymous requests cannot restart a server even when supplied a CSRF token.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRequireLogin_whenRestartIsAnonymous() throws Exception {
        // GIVEN
        var request = post( "/server/restart" ).param( "launchId", "current-launch" ).with( csrf() );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().is3xxRedirection() ).andExpect( redirectedUrl( "/login" ) );
        verify( runtime, never() ).stop( any() );
    }

    /**
     * POST Restart retains the application's CSRF protection.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRejectRestart_whenCsrfIsMissing() throws Exception {
        // GIVEN
        var request = post( "/server/restart" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isForbidden() );
        verify( runtime, never() ).stop( any() );
    }

    /**
     * HTTP completes after the graceful stop is admitted, while the process exit and the new start are still
     * pending.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectAfterAdmission_whenRestartIsValid() throws Exception {
        // GIVEN
        var completion = new CompletableFuture< Void >();
        given( runtime.stop( any() ) ).willReturn( completion );
        // WHEN
        var result = underTest.perform( post( "/server/restart" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );
        // THEN
        result.andExpect( redirectedUrl( "/" ) ).andExpect( flash().attribute( "serverRestarting", true ) );
        var request = ArgumentCaptor.forClass( StopRequest.class );
        verify( runtime ).stop( request.capture() );
        assertThat( request.getValue().isForceConfirmed() ).isFalse();
        assertThat( request.getValue().getLaunchId() ).isEqualTo( "current-launch" );
        assertThat( completion ).isNotDone();
        verify( runtime, never() ).start( any(), any() );
    }

    /**
     * A runtime race is translated by the feature advice into the restart message rather than an HTTP 500.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldPresentRejection_whenRuntimeRefusesStaleRestart() throws Exception {
        // GIVEN
        given( runtime.stop( any() ) ).willThrow( new IllegalStateException( "Stale launch" ) );
        // WHEN
        var result = underTest.perform( post( "/server/restart" ).param( "launchId", "old-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );
        // THEN
        result.andExpect( redirectedUrl( "/" ) ).andExpect( flash().attribute( "serverErrorKey",
                ServerMessage.RESTART_REJECTED.getKey() ) );
    }

    /**
     * The polled island of a running server carries an enabled restart form bound to the current launch.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderEnabledRestartForm_whenServerIsRunning() throws Exception {
        // GIVEN
        var request = get( "/server/status" ).with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isOk() );
        assertThat( restartForm( result.andReturn().getResponse().getContentAsString() ) )
                .contains( "current-launch", "icon-restart" ).doesNotContain( "disabled" );
    }

    /**
     * A stopped server has nothing to restart, so the island renders the restart action disabled.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderDisabledRestartForm_whenServerIsStopped() throws Exception {
        // GIVEN
        given( runtime.status() ).willReturn( ServerStatus.stopped() );
        // WHEN
        var result = underTest.perform( get( "/server/status" ).with( user( "admin" ).roles( "ADMIN" ) ) );
        // THEN
        result.andExpect( status().isOk() );
        assertThat( restartForm( result.andReturn().getResponse().getContentAsString() ) )
                .contains( "icon-restart", "disabled" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================
    /**
     * Isolates every application path and SQLite data for this integration context.
     *
     * @param registry
     *         Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void properties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "vdeck.db" ) );
    }

    /** Cuts the restart form out of the rendered island, so assertions cannot match a neighbouring action. */
    private static String restartForm( String html ) {
        int start = html.indexOf( "action=\"/server/restart\"" );
        assertThat( start ).as( "restart form" ).isNotNegative();
        return html.substring( start, html.indexOf( "</form>", start ) );
    }
}
