package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

    @Autowired
    private UserRepository users;

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Removes every account so that the next, recreated context initializes the default administrator
     * again. The Liquibase-managed database file outlives each context.
     */
    @AfterEach
    void removeAccounts() {
        users.deleteAll();
    }

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
     * replacement of the initial password while still using it.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRedirectToInitialPasswordChange_whenInitialAdministratorLogsIn() throws Exception {
        // GIVEN
        // the admin/admin account created by DataInitializer

        // WHEN
        var result = underTest.perform( formLogin().user( "admin" ).password( "admin" ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/initial-password-change" ) );
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
                .andExpect( redirectedUrl( "/initial-password-change" ) );
    }

    /**
     * Verifies that a first-login session cannot reach the ordinary change screen either, so the
     * account is never asked for a current password it has never chosen.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldBlockOrdinaryPasswordChange_whenInitialPasswordWasNotReplaced() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var result = underTest.perform( get( "/change-password" ).session( session ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/initial-password-change" ) );
    }

    /**
     * Verifies that an administrator with a password of their own choosing can no longer reach the
     * screen that replaces a password without verifying the current one.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRejectInitialPasswordChange_whenPasswordWasAlreadyReplaced() throws Exception {
        // GIVEN
        replaceInitialPassword();
        MockHttpSession session = loginWithPassword( "Replacement-1" );

        // WHEN
        var result = underTest.perform( get( "/initial-password-change" ).session( session ) );

        // THEN
        result.andExpect( status().isForbidden() );
    }

    /**
     * Verifies that initial password replacement requires CSRF protection even for an authenticated
     * first-login session.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRejectInitialPasswordChange_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var result = underTest.perform( post( "/initial-password-change" )
                .session( session )
                .param( "newPassword", "Replacement-1" )
                .param( "confirmation", "Replacement-1" ) );

        // THEN
        result.andExpect( status().isForbidden() );
    }

    /**
     * Verifies that a successful initial replacement invalidates the first-login session, rejects the
     * initial password and accepts the new password using normal saved-request routing.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRequireNewLoginWithReplacementPassword_whenInitialPasswordChanges() throws Exception {
        // GIVEN
        MockHttpSession session = loginWithInitialPassword();

        // WHEN
        var changeResult = underTest.perform( post( "/initial-password-change" )
                .session( session )
                .with( csrf() )
                .param( "newPassword", "Replacement-1" )
                .param( "confirmation", "Replacement-1" ) );

        // THEN
        changeResult.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
        underTest.perform( formLogin().user( "admin" ).password( "admin" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login?error" ) );
        underTest.perform( formLogin().user( "admin" ).password( "Replacement-1" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/" ) );
    }

    /**
     * Verifies that an administrator who already works with the panel can change the password from the
     * island, has to prove the current one and is logged out afterwards.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldRequireCurrentPassword_whenPasswordIsChangedFromTheIsland() throws Exception {
        // GIVEN
        replaceInitialPassword();
        MockHttpSession session = loginWithPassword( "Replacement-1" );

        // WHEN
        var rejectedResult = underTest.perform( post( "/change-password" )
                .session( session )
                .with( csrf() )
                .param( "currentPassword", "wrong-password" )
                .param( "newPassword", "Replacement-2" )
                .param( "confirmation", "Replacement-2" ) );

        // THEN
        rejectedResult.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/change-password" ) )
                .andExpect( flash().attribute( "passwordChangeErrorKey",
                        "changePassword.error.currentPasswordIncorrect" ) );
        underTest.perform( post( "/change-password" )
                        .session( session )
                        .with( csrf() )
                        .param( "currentPassword", "Replacement-1" )
                        .param( "newPassword", "Replacement-2" )
                        .param( "confirmation", "Replacement-2" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
        underTest.perform( formLogin().user( "admin" ).password( "Replacement-2" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/" ) );
    }

    /**
     * Verifies that the island logout button reaches Spring Security's default logout, which ends the
     * server-side session instead of only clearing the browser view.
     *
     * @throws Exception
     *         if MockMvc cannot execute the requests
     */
    @Test
    void shouldEndSession_whenLogoutIsSubmitted() throws Exception {
        // GIVEN
        replaceInitialPassword();
        MockHttpSession session = loginWithPassword( "Replacement-1" );

        // WHEN
        var result = underTest.perform( post( "/logout" ).session( session ).with( csrf() ) );

        // THEN
        result.andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login?logout" ) );
        underTest.perform( get( "/" ).session( session ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
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
        MockHttpSession firstSession = loginWithPassword( "Replacement-1" );

        // WHEN
        MockHttpSession secondSession = loginWithPassword( "Replacement-1" );

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
        underTest.perform( post( "/initial-password-change" )
                        .session( initialSession )
                        .with( csrf() )
                        .param( "newPassword", "Replacement-1" )
                        .param( "confirmation", "Replacement-1" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/login" ) );
    }
}
