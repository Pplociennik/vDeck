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
 * the default password. Only the initial-password replacement screen, logout and the static resources
 * rendering that screen remain reachable; the ordinary password-change screen, which asks for the
 * current password, is not among them.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String INITIAL_PASSWORD_CHANGE_PATH = "/initial-password-change";

    private static final Set< String > ALLOWED_PATHS = Set.of( INITIAL_PASSWORD_CHANGE_PATH, "/logout" );

    /**
     * Static resource directories the initial-password replacement screen needs in order to render: its
     * stylesheets, the icon sprite and the interface textures. The exception is limited to these
     * directories so it cannot open any application endpoint, and every request matching it still
     * has to pass the authorization rules of the security chain.
     */
    private static final Set< String > ALLOWED_RESOURCE_PREFIXES = Set.of( "/css/", "/icons/", "/img/" );

    /**
     * Creates the filter enforcing mandatory replacement of initial credentials.
     */
    public PasswordChangeRequiredFilter() {
    }

    /**
     * Restricts first-login users to initial-password replacement, logout and the static resources of
     * that screen.
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
        if ( requiresPasswordChange( authentication ) && !isAllowedBeforePasswordChange( requestPath ) ) {
            response.sendRedirect( response.encodeRedirectURL(
                    request.getContextPath() + INITIAL_PASSWORD_CHANGE_PATH ) );
            return;
        }
        filterChain.doFilter( request, response );
    }

    private boolean isAllowedBeforePasswordChange( String requestPath ) {
        return ALLOWED_PATHS.contains( requestPath )
                || ALLOWED_RESOURCE_PREFIXES.stream().anyMatch( requestPath::startsWith );
    }

    private boolean requiresPasswordChange( Authentication authentication ) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch( authority -> PanelUserService.PASSWORD_CHANGE_REQUIRED_AUTHORITY
                        .equals( authority.getAuthority() ) );
    }
}
