package com.github.pplociennik.vdeck.feature.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the complete generated-login, initial-credential restriction and password replacement flow
 * against the real Spring Security chain and an isolated SQLite database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD )
class SecurityFlowIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an anonymous panel request is redirected to Spring Security's generated login page.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToGeneratedLogin_whenRequestIsAnonymous() throws Exception {
        // GIVEN
        // an application initialized with the default administrator

        // WHEN
        var result = underTest.perform( get( "/" ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
    }

    /**
     * Verifies that the initialized administrator can authenticate and is routed directly to mandatory
     * password replacement while using the initial password.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToPasswordChange_whenInitialAdministratorLogsIn() throws Exception {
        // GIVEN
        // the admin/admin account created by DataInitializer

        // WHEN
        var result = underTest.perform( formLogin().user( "admin" ).password( "admin" ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/change-password" ) );
    }

    /**
     * Verifies that a first-login session cannot reach an ordinary panel URL before replacing the password.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldBlockPanelAccess_whenInitialPasswordWasNotReplaced() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var result = underTest.perform( get( "/servers" ).session( session ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/change-password" ) );
    }

    /**
     * Verifies that password replacement requires CSRF protection even for an authenticated first-login session.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRejectPasswordChange_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var result = underTest.perform( post( "/change-password" )
                .session( session )
                .param( "currentPassword", "admin" )
                .param( "newPassword", "replacement-password" )
                .param( "confirmation", "replacement-password" ) );

        // THEN
        result.andExpect( status().isForbidden() );
    }

    /**
     * Verifies that a successful replacement invalidates the first-login session, rejects the initial
     * password and accepts the new password using normal saved-request routing.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRequireNewLoginWithReplacementPassword_whenPasswordChanges() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var changeResult = underTest.perform( post( "/change-password" )
                .session( session )
                .with( csrf() )
                .param( "currentPassword", "admin" )
                .param( "newPassword", "replacement-password" )
                .param( "confirmation", "replacement-password" ) );

        // THEN
        changeResult.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
        underTest.perform( formLogin().user( "admin" ).password( "admin" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login?error" ) );
        underTest.perform( formLogin().user( "admin" ).password( "replacement-password" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/" ) );
    }

    /**
     * Verifies that a newer administrator login expires the previously active session.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldExpirePreviousSession_whenAdministratorLogsInAgain() throws Exception {
        // GIVEN
        replaceInitialPassword();
        MockHttpSession firstSession = loginWithPassword( "replacement-password" );

        // WHEN
        MockHttpSession secondSession = loginWithPassword( "replacement-password" );

        // THEN
        underTest.perform( get( "/servers" ).session( firstSession ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login?expired" ) );
        underTest.perform( get( "/servers" ).session( secondSession ) )
                .andExpect( status().isNotFound() );
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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "security-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }

    private MockHttpSession loginWithInitialPassword() throws Exception {
        return loginWithPassword( "admin" );
    }

    private MockHttpSession loginWithPassword( String password ) throws Exception {
        MvcResult loginResult = underTest.perform( formLogin().user( "admin" ).password( password ) )
                .andExpect( status().is3xxRedirection() )
                .andReturn();
        return ( MockHttpSession ) loginResult.getRequest().getSession( false );
    }

    private void replaceInitialPassword() throws Exception {
        MockHttpSession initialSession = loginWithInitialPassword();
        underTest.perform( post( "/change-password" )
                        .session( initialSession )
                        .with( csrf() )
                        .param( "currentPassword", "admin" )
                        .param( "newPassword", "replacement-password" )
                        .param( "confirmation", "replacement-password" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
    }
}
