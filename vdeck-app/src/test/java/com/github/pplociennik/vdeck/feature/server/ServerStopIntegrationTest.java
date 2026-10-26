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
import com.github.pplociennik.vdeck.runtime.api.StopFailure;
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

/** Verifies island forms and real MVC/Security contracts without creating or signaling real processes. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ServerStopIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    @TempDir
    private static Path directory;
    @Autowired
    private MockMvc underTest;
    @MockitoBean
    private ServerRuntime runtime;

    private ServerStatus running;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Prepares a correlated managed launch; only the runtime boundary is substituted. */
    @BeforeEach
    void setUp() {
        ProcessHandle handle = mock( ProcessHandle.class );
        given( handle.pid() ).willReturn( 42L );
        running = ServerStatus.running( new RunningServer( handle, Instant.EPOCH, directory.resolve( "run.log" ) ),
                "current-launch" );
        given( runtime.status() ).willReturn( running );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** Anonymous requests cannot stop a server even when supplied a CSRF token. */
    @Test
    void shouldRequireLogin_whenStopIsAnonymous() throws Exception {
        // GIVEN
        var request = post( "/server/stop" ).param( "launchId", "current-launch" ).with( csrf() );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().is3xxRedirection() ).andExpect( redirectedUrl( "/login" ) );
        verify( runtime, never() ).stop( any() );
    }

    /** Non-admin users cannot submit an otherwise valid Kill. */
    @Test
    void shouldRejectKill_whenRoleIsNotAdmin() throws Exception {
        // GIVEN
        var request = post( "/server/kill" ).param( "launchId", "current-launch" ).param( "confirmed", "true" )
                .with( user( "reader" ).roles( "USER" ) ).with( csrf() );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isForbidden() );
        verify( runtime, never() ).stop( any() );
    }

    /** POST Stop retains the application's CSRF protection. */
    @Test
    void shouldRejectStop_whenCsrfIsMissing() throws Exception {
        // GIVEN
        var request = post( "/server/stop" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isForbidden() );
        verify( runtime, never() ).stop( any() );
    }

    /** HTTP completes after admission while the process-exit future remains unfinished. */
    @Test
    void shouldRedirectAfterAdmission_whenStopIsValid() throws Exception {
        // GIVEN
        var completion = new CompletableFuture< Void >();
        given( runtime.stop( any() ) ).willReturn( completion );
        // WHEN
        var result = underTest.perform( post( "/server/stop" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );
        // THEN
        result.andExpect( redirectedUrl( "/" ) ).andExpect( flash().attribute( "serverStopping", true ) );
        var request = ArgumentCaptor.forClass( StopRequest.class );
        verify( runtime ).stop( request.capture() );
        assertThat( request.getValue().isForceConfirmed() ).isFalse();
        assertThat( completion ).isNotDone();
    }

    /** A confirmation GET never performs a snapshot, signal or stop operation. */
    @Test
    void shouldOnlyPresentRisk_whenConfirmationIsOpened() throws Exception {
        // GIVEN
        var request = get( "/server/kill-confirmation" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isOk() );
        String html = result.andReturn().getResponse().getContentAsString();
        assertThat( html ).contains( "name=\"confirmed\"", "name=\"launchId\"", "/server/kill", "current-launch" );
        assertThat( html ).contains( "także gdy kopia nie powstanie" ).doesNotContain( "snapshotBypassConfirmed" );
        verify( runtime, never() ).stop( any() );
    }

    /** Missing risk confirmation is rejected server-side, including clients without JavaScript. */
    @Test
    void shouldRejectKill_whenRiskIsNotConfirmed() throws Exception {
        // GIVEN
        var request = post( "/server/kill" ).param( "launchId", "current-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( redirectedUrl( "/server/kill-confirmation?launchId=current-launch" ) )
                .andExpect( flash().attribute( "serverErrorKey",
                ServerMessage.CONFIRMATION_REQUIRED.getKey() ) );
        verify( runtime, never() ).stop( any() );
    }

    /** Explicitly accepted risk grants force only to the submitted current launch. */
    @Test
    void shouldGrantManualForcePermission_whenKillIsConfirmed() throws Exception {
        // GIVEN
        given( runtime.stop( any() ) ).willReturn( new CompletableFuture<>() );
        // WHEN
        var result = underTest.perform( post( "/server/kill" ).param( "launchId", "current-launch" )
                .param( "confirmed", "true" ).with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );
        // THEN
        result.andExpect( redirectedUrl( "/" ) );
        var request = ArgumentCaptor.forClass( StopRequest.class );
        verify( runtime ).stop( request.capture() );
        assertThat( request.getValue().isForceConfirmed() ).isTrue();
        assertThat( request.getValue().getLaunchId() ).isEqualTo( "current-launch" );
    }

    /** A confirmation page from another launch cannot authorize a new server. */
    @Test
    void shouldRejectConfirmation_whenLaunchIsStale() throws Exception {
        // GIVEN
        var request = get( "/server/kill-confirmation" ).param( "launchId", "old-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( redirectedUrl( "/" ) ).andExpect( flash().attribute( "serverErrorKey",
                ServerMessage.KILL_REJECTED.getKey() ) );
    }

    /** A runtime race is translated by the feature advice rather than becoming an HTTP 500. */
    @Test
    void shouldPresentRejection_whenRuntimeRefusesStaleStop() throws Exception {
        // GIVEN
        given( runtime.stop( any() ) ).willThrow( new IllegalStateException( "Stale launch" ) );
        // WHEN
        var result = underTest.perform( post( "/server/stop" ).param( "launchId", "old-launch" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );
        // THEN
        result.andExpect( redirectedUrl( "/" ) ).andExpect( flash().attribute( "serverErrorKey",
                ServerMessage.STOP_REJECTED.getKey() ) );
    }

    /** The polled island carries current correlation and real Stop/Kill destinations. */
    @Test
    void shouldRenderActionForms_whenStatusFragmentIsRequested() throws Exception {
        // GIVEN
        var request = get( "/server/status" ).with( user( "admin" ).roles( "ADMIN" ) );
        // WHEN
        var result = underTest.perform( request );
        // THEN
        result.andExpect( status().isOk() );
        assertThat( result.andReturn().getResponse().getContentAsString() )
                .contains( "data-server-island", "/server/stop", "/server/kill-confirmation", "current-launch" );
    }

    /** Missing source files have a distinct, translated diagnostic and keep a confirmed Kill available. */
    @Test
    void shouldExplainMissingWorldSave_whenStatusFragmentIsRequested() throws Exception {
        // GIVEN
        ProcessHandle handle = mock( ProcessHandle.class );
        given( handle.pid() ).willReturn( 42L );
        var failed = ServerStatus.stopFailed( new RunningServer( handle, Instant.EPOCH, directory.resolve( "run.log" ) ),
                "current-launch", StopFailure.WORLD_SAVE_MISSING );
        given( runtime.status() ).willReturn( failed );
        // WHEN
        var result = underTest.perform( get( "/server/status" ).with( user( "admin" ).roles( "ADMIN" ) ) );
        // THEN
        result.andExpect( status().isOk() );
        assertThat( result.andReturn().getResponse().getContentAsString() )
                .contains( "Brakuje wymaganych plików zapisu świata", "bez backupu", "/server/kill-confirmation" )
                .doesNotContain( "server.stopFailure.WORLD_SAVE_MISSING", "UncheckedIOException" );
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

}
