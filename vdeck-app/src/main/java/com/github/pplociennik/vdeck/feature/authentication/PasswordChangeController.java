package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
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
 * Renders and processes a password change requested by a user already working with the panel.
 * The screen is opened from the action island at any time and asks for the current password, because
 * the account is not known to be using credentials it never chose. Replacement of the initial password
 * is a separate flow owned by {@link InitialPasswordChangeController}.
 */
@Controller
class PasswordChangeController {

    private static final String CHANGE_PASSWORD_VIEW = "authentication/change-password";

    /**
     * Model attribute holding the form backing object. The controller publishes an empty instance both
     * when opening the screen and after a failure, so a submitted password is never sent back.
     */
    private static final String FORM_ATTRIBUTE = "passwordChangeRequest";

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
        model.addAttribute( FORM_ATTRIBUTE, emptyRequest() );
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
     * @return redirect to login after success
     * @throws PasswordChangeException
     *         if the fields fail annotation validation or a check rejects the replacement; the advice
     *         answers it by redirecting back to this screen with the message
     */
    @PostMapping( "/change-password" )
    @Nonnull
    String changePassword(
            @Valid @ModelAttribute( FORM_ATTRIBUTE )
            @Nonnull PasswordChangeRequest passwordChangeRequest,
            @Nonnull BindingResult bindingResult,
            @Nonnull Authentication authentication,
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response ) {
        requireNonNull( passwordChangeRequest, "passwordChangeRequest must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );
        requireNonNull( authentication, "authentication must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( response, "response must not be null" );

        if ( bindingResult.hasErrors() ) {
            throw new PasswordChangeException( PasswordChangeMessage.INVALID_INPUT );
        }

        panelUserService.changePassword( authentication.getName(), passwordChangeRequest );
        logoutHandler.logout( request, response, authentication );
        return "redirect:/login";
    }

    private @Nonnull PasswordChangeRequest emptyRequest() {
        return new PasswordChangeRequest( null, null, null );
    }
}