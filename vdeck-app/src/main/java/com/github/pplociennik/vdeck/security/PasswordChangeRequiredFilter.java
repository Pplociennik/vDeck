package com.github.pplociennik.vdeck.security;

import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Prevents an authenticated user with initial credentials from accessing the panel before replacing
 * the default password.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/change-password";

    private static final Set< String > ALLOWED_PATHS = Set.of( CHANGE_PASSWORD_PATH, "/logout" );

    /**
     * Creates the filter enforcing mandatory replacement of initial credentials.
     */
    public PasswordChangeRequiredFilter() {
    }

    /**
     * Restricts first-login users to password replacement and logout.
     *
     * @param request
     *         current servlet request
     * @param response
     *         current servlet response
     * @param filterChain
     *         remaining security and application filters
     * @throws ServletException
     *         if downstream request processing fails
     * @throws IOException
     *         if downstream processing or redirect writing fails
     */
    @Override
    protected void doFilterInternal( @Nonnull HttpServletRequest request,
                                     @Nonnull HttpServletResponse response,
                                     @Nonnull FilterChain filterChain )
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String requestPath = request.getRequestURI().substring( request.getContextPath().length() );
        if ( requiresPasswordChange( authentication ) && !ALLOWED_PATHS.contains( requestPath ) ) {
            response.sendRedirect( response.encodeRedirectURL(
                    request.getContextPath() + CHANGE_PASSWORD_PATH ) );
            return;
        }
        filterChain.doFilter( request, response );
    }

    private boolean requiresPasswordChange( Authentication authentication ) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch( authority -> PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY
                        .equals( authority.getAuthority() ) );
    }
}
