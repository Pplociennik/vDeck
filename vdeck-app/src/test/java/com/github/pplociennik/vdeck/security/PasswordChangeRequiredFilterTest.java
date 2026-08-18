package com.github.pplociennik.vdeck.security;

import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies request restrictions applied by {@link PasswordChangeRequiredFilter} to users still using
 * their initial password.
 */
class PasswordChangeRequiredFilterTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private MockFilterChain filterChain;

    private PasswordChangeRequiredFilter underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh filter and servlet exchange before each test.
     */
    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        underTest = new PasswordChangeRequiredFilter();
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Clears thread-local authentication so tests cannot affect one another.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a restricted user requesting an ordinary panel URL is redirected to password change.
     */
    @Test
    void shouldRedirectToPasswordChange_whenRestrictedUserRequestsPanel() throws Exception {
        // GIVEN
        authenticate( "ROLE_ADMIN", PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY );
        request.setRequestURI( "/servers" );

        // WHEN
        underTest.doFilterInternal( request, response, filterChain );

        // THEN
        assertThat( response.getRedirectedUrl() ).isEqualTo( "/change-password" );
        assertThat( filterChain.getRequest() ).isNull();
    }

    /**
     * Verifies that the restricted user can reach password replacement and logout while all other
     * requests remain blocked.
     *
     * @param allowedPath
     *         whitelisted first-login request path
     */
    @ParameterizedTest( name = "[{index}] allowedPath={0}" )
    @ValueSource( strings = { "/change-password", "/logout" } )
    void shouldContinueFilterChain_whenRestrictedUserRequestsAllowedPath( String allowedPath ) throws Exception {
        // GIVEN
        authenticate( "ROLE_ADMIN", PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY );
        request.setRequestURI( allowedPath );

        // WHEN
        underTest.doFilterInternal( request, response, filterChain );

        // THEN
        assertThat( filterChain.getRequest() ).isSameAs( request );
        assertThat( response.getRedirectedUrl() ).isNull();
    }

    /**
     * Verifies that an administrator who replaced the initial password is not redirected.
     */
    @Test
    void shouldContinueFilterChain_whenUserHasNoPasswordChangeAuthority() throws Exception {
        // GIVEN
        authenticate( "ROLE_ADMIN" );
        request.setRequestURI( "/servers" );

        // WHEN
        underTest.doFilterInternal( request, response, filterChain );

        // THEN
        assertThat( filterChain.getRequest() ).isSameAs( request );
    }

    /**
     * Verifies that anonymous requests continue to Spring Security's authorization filters.
     */
    @Test
    void shouldContinueFilterChain_whenRequestIsAnonymous() throws Exception {
        // GIVEN
        request.setRequestURI( "/servers" );

        // WHEN
        underTest.doFilterInternal( request, response, filterChain );

        // THEN
        assertThat( filterChain.getRequest() ).isSameAs( request );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void authenticate( String... authorities ) {
        List< SimpleGrantedAuthority > grantedAuthorities = List.of( authorities ).stream()
                .map( SimpleGrantedAuthority::new )
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin", "<redacted>", grantedAuthorities );
        SecurityContextHolder.getContext().setAuthentication( authentication );
    }
}
