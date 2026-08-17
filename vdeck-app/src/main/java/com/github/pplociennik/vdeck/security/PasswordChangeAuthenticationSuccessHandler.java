package com.github.pplociennik.vdeck.security;

import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Redirects first-login users to mandatory password replacement and otherwise preserves Spring
 * Security's saved-request behavior.
 */
public class PasswordChangeAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String CHANGE_PASSWORD_PATH = "/change-password";

    private final AuthenticationSuccessHandler defaultSuccessHandler;

    /**
     * Creates a handler delegating unrestricted logins to Spring Security's saved-request handler.
     */
    public PasswordChangeAuthenticationSuccessHandler() {
        this( new SavedRequestAwareAuthenticationSuccessHandler() );
    }

    PasswordChangeAuthenticationSuccessHandler( @Nonnull AuthenticationSuccessHandler defaultSuccessHandler ) {
        this.defaultSuccessHandler = requireNonNull(
                defaultSuccessHandler, "defaultSuccessHandler must not be null" );
    }

    /**
     * Redirects restricted users directly to password replacement and delegates all other successes.
     *
     * @param request
     *         successful login request
     * @param response
     *         response receiving the redirect
     * @param authentication
     *         successful authentication and its authorities
     * @throws IOException
     *         if writing the redirect fails
     * @throws ServletException
     *         if the delegated default handler cannot complete
     */
    @Override
    public void onAuthenticationSuccess( @Nonnull HttpServletRequest request,
                                         @Nonnull HttpServletResponse response,
                                         @Nonnull Authentication authentication )
            throws IOException, ServletException {
        requireNonNull( request, "request must not be null" );
        requireNonNull( response, "response must not be null" );
        requireNonNull( authentication, "authentication must not be null" );
        if ( requiresPasswordChange( authentication ) ) {
            response.sendRedirect( response.encodeRedirectURL( request.getContextPath() + CHANGE_PASSWORD_PATH ) );
            return;
        }
        defaultSuccessHandler.onAuthenticationSuccess( request, response, authentication );
    }

    private boolean requiresPasswordChange( @Nonnull Authentication authentication ) {
        return authentication.getAuthorities().stream()
                .anyMatch( authority -> PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY
                        .equals( authority.getAuthority() ) );
    }
}
