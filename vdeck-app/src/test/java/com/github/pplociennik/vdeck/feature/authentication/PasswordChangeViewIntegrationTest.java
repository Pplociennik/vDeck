package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies that both password screens are rendered by the real Thymeleaf engine together with the
 * configured message source, so a broken template or an unresolved translation key is caught by the
 * build instead of by the running panel. The screens differ in exactly two visible ways, both asserted
 * here: only the ordinary change asks for the current password, and only it keeps the panel navigation -
 * the action island and the side menu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class PasswordChangeViewIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Prefix of every translation key of these screens. An unresolved key is rendered as the key itself,
     * so its absence in the response proves that the texts came from the message source.
     */
    private static final String TRANSLATION_KEY_PREFIX = "changePassword.";

    /**
     * Prefix of every translation key of the action island, embedded by the ordinary change screen.
     */
    private static final String ISLAND_TRANSLATION_KEY_PREFIX = "island.";

    /**
     * Prefix of every translation key of the side menu, embedded by the ordinary change screen.
     */
    private static final String SIDE_MENU_TRANSLATION_KEY_PREFIX = "sideMenu.";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the ordinary change screen renders with its texts resolved, asks for the current
     * password and keeps the action island and the side menu, so the user can leave the screen without
     * changing anything.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderChangePasswordScreenWithIsland_whenUserIsAuthenticated() throws Exception {
        // GIVEN
        // an administrator who already replaced the initial password

        // WHEN
        var result = underTest.perform( get( "/change-password" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "authentication/change-password" ) )
                .andExpect( content().string( not( containsString( TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( not( containsString( ISLAND_TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( containsString( "name=\"currentPassword\"" ) ) )
                .andExpect( content().string( containsString( "class=\"island surface surface--island\"" ) ) )
                .andExpect( content().string( not( containsString( SIDE_MENU_TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( containsString( "data-side-menu-toggle" ) ) )
                .andExpect( content().string( containsString( "aria-describedby=\"password-rules\"" ) ) );
    }

    /**
     * Verifies that the mandatory screen renders with its texts resolved, asks for no current password
     * and offers neither the island nor the side menu, so a first-login account cannot reach any other
     * panel action from it.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRenderInitialPasswordChangeScreenWithoutIsland_whenInitialPasswordIsStillActive()
            throws Exception {
        // GIVEN
        // an administrator still using the initial password

        // WHEN
        var result = underTest.perform( get( "/initial-password-change" ).with( restrictedAdministrator() ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( view().name( "authentication/initial-password-change" ) )
                .andExpect( content().string( not( containsString( TRANSLATION_KEY_PREFIX ) ) ) )
                .andExpect( content().string( not( containsString( "name=\"currentPassword\"" ) ) ) )
                .andExpect( content().string( not( containsString( "class=\"island" ) ) ) )
                .andExpect( content().string( not( containsString( "data-side-menu" ) ) ) )
                .andExpect( content().string( containsString( "aria-describedby=\"password-rules\"" ) ) );
    }

    /**
     * Verifies that an administrator with a password of their own choosing cannot open the screen that
     * replaces a password without asking for the current one.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRejectInitialPasswordChangeScreen_whenPasswordWasAlreadyReplaced() throws Exception {
        // GIVEN
        // an administrator who no longer carries the first-login authority

        // WHEN
        var result = underTest.perform(
                get( "/initial-password-change" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isForbidden() );
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
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "view-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "create-drop" );
    }

    /**
     * Builds an administrator that still carries the first-login restriction. Both authorities are
     * passed in one call, because the request post-processor replaces the whole set every time.
     *
     * @return post-processor authenticating an account whose initial password is still active
     */
    private RequestPostProcessor restrictedAdministrator() {
        return user( "admin" ).authorities(
                new SimpleGrantedAuthority( "ROLE_ADMIN" ),
                new SimpleGrantedAuthority( PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY ) );
    }
}
