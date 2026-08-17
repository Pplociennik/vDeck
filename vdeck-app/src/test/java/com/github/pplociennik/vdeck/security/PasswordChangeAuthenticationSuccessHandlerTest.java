package com.github.pplociennik.vdeck.security;

import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies first-login routing and default saved-request delegation performed by
 * {@link PasswordChangeAuthenticationSuccessHandler}.
 */
@ExtendWith( MockitoExtension.class )
class PasswordChangeAuthenticationSuccessHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private AuthenticationSuccessHandler defaultSuccessHandler;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private PasswordChangeAuthenticationSuccessHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an isolated handler and servlet exchange before every test.
     */
    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        underTest = new PasswordChangeAuthenticationSuccessHandler( defaultSuccessHandler );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an account still using initial credentials is redirected directly to password
     * replacement instead of its saved request.
     */
    @Test
    void shouldRedirectToPasswordChange_whenAuthorityRequiresReplacement() throws Exception {
        // GIVEN
        Authentication authentication = authenticationWithAuthorities(
                "ROLE_ADMIN", PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY );

        // WHEN
        underTest.onAuthenticationSuccess( request, response, authentication );

        // THEN
        assertThat( response.getRedirectedUrl() ).isEqualTo( "/change-password" );
        verify( defaultSuccessHandler, never() ).onAuthenticationSuccess( request, response, authentication );
    }

    /**
     * Verifies that an unrestricted administrator retains Spring Security's normal saved-request behavior.
     */
    @Test
    void shouldDelegateToDefaultHandler_whenPasswordWasReplaced() throws Exception {
        // GIVEN
        Authentication authentication = authenticationWithAuthorities( "ROLE_ADMIN" );

        // WHEN
        underTest.onAuthenticationSuccess( request, response, authentication );

        // THEN
        verify( defaultSuccessHandler ).onAuthenticationSuccess( request, response, authentication );
        assertThat( response.getRedirectedUrl() ).isNull();
    }

    /**
     * Verifies that a null default handler is rejected by the injectable constructor contract.
     */
    @Test
    void shouldThrowException_whenDefaultHandlerIsNull() {
        // GIVEN
        // no delegated success handler

        // WHEN
        Throwable thrown = catchThrowable( () -> new PasswordChangeAuthenticationSuccessHandler( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "defaultSuccessHandler must not be null" );
    }

    /**
     * Verifies that a null servlet request is rejected before routing decisions are made.
     */
    @Test
    void shouldThrowException_whenRequestIsNull() {
        // GIVEN
        Authentication authentication = authenticationWithAuthorities( "ROLE_ADMIN" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.onAuthenticationSuccess( null, response, authentication ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "request must not be null" );
    }

    /**
     * Verifies that a null servlet response is rejected before routing decisions are made.
     */
    @Test
    void shouldThrowException_whenResponseIsNull() {
        // GIVEN
        Authentication authentication = authenticationWithAuthorities( "ROLE_ADMIN" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.onAuthenticationSuccess( request, null, authentication ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "response must not be null" );
    }

    /**
     * Verifies that a null authentication is rejected before authorities are inspected.
     */
    @Test
    void shouldThrowException_whenAuthenticationIsNull() {
        // GIVEN
        // no authentication

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.onAuthenticationSuccess( request, response, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "authentication must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private Authentication authenticationWithAuthorities( String... authorities ) {
        List< SimpleGrantedAuthority > grantedAuthorities = List.of( authorities ).stream()
                .map( SimpleGrantedAuthority::new )
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated( "admin", "<redacted>", grantedAuthorities );
    }
}
