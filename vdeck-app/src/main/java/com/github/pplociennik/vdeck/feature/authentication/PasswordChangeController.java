package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.domain.PasswordChangeResult;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import static java.util.Objects.requireNonNull;

/**
 * Renders and processes the mandatory first-login password replacement.
 */
@Controller
class PasswordChangeController {

    private static final String CHANGE_PASSWORD_VIEW = "authentication/change-password";

    private final PanelUserService panelUserService;

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * Creates the controller delegating credential changes to the panel-user service.
     *
     * @param panelUserService
     *         service owning password validation and persistence
     */
    PasswordChangeController( @Nonnull PanelUserService panelUserService ) {
        this.panelUserService = requireNonNull( panelUserService, "panelUserService must not be null" );
    }

    /**
     * Displays an empty password-change form.
     *
     * @param model
     *         MVC model receiving the empty form
     * @return password-change template name
     */
    @GetMapping( "/change-password" )
    @Nonnull
    String showPasswordChange( @Nonnull Model model ) {
        model.addAttribute( "passwordChangeRequest", emptyRequest() );
        return CHANGE_PASSWORD_VIEW;
    }

    /**
     * Validates and applies a password replacement, invalidating the authenticated session on success.
     *
     * @param passwordChangeRequest
     *         submitted password fields
     * @param bindingResult
     *         validation result produced from the request annotations
     * @param authentication
     *         currently authenticated panel user
     * @param request
     *         servlet request whose session is invalidated after success
     * @param response
     *         servlet response updated by the logout handler
     * @param model
     *         MVC model receiving a safe validation message after failure
     * @return redirect to login after success, otherwise the password-change template name
     */
    @PostMapping( "/change-password" )
    @Nonnull
    String changePassword(
            @Valid @ModelAttribute( "passwordChangeRequest" )
            @Nonnull PasswordChangeRequest passwordChangeRequest,
            @Nonnull BindingResult bindingResult,
            @Nonnull Authentication authentication,
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull Model model ) {
        requireNonNull( passwordChangeRequest, "passwordChangeRequest must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );
        requireNonNull( authentication, "authentication must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( response, "response must not be null" );
        requireNonNull( model, "model must not be null" );

        if ( bindingResult.hasErrors() ) {
            model.addAttribute( "passwordChangeRequest", emptyRequest() );
            model.addAttribute(
                    "passwordChangeError", "Every field is required and the new password must contain "
                            + "between 12 and 128 characters." );
            return CHANGE_PASSWORD_VIEW;
        }

        PasswordChangeResult result = panelUserService.changePassword(
                authentication.getName(), passwordChangeRequest );
        if ( result == PasswordChangeResult.SUCCESS ) {
            logoutHandler.logout( request, response, authentication );
            return "redirect:/login";
        }

        model.addAttribute( "passwordChangeRequest", emptyRequest() );
        model.addAttribute( "passwordChangeError", errorMessage( result ) );
        return CHANGE_PASSWORD_VIEW;
    }

    private @Nonnull String errorMessage( @Nonnull PasswordChangeResult result ) {
        return switch ( result ) {
            case CURRENT_PASSWORD_INCORRECT -> "The current password is incorrect.";
            case NEW_PASSWORD_MISMATCH -> "The new password and confirmation do not match.";
            case NEW_PASSWORD_UNCHANGED -> "The new password must differ from the current password.";
            case SUCCESS -> throw new IllegalArgumentException( "A successful result has no error message" );
        };
    }

    private @Nonnull PasswordChangeRequest emptyRequest() {
        return new PasswordChangeRequest( null, null, null );
    }
}
