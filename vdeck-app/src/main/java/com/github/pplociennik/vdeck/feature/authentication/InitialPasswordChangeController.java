package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.dto.InitialPasswordChangeRequest;
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
 * Renders and processes the mandatory replacement of the initial administrator password.
 * The security chain admits only accounts holding
 * {@link PanelUserService#PASSWORD_CHANGE_REQUIRED_AUTHORITY}, so the screen asks for no current
 * password - the account still uses credentials it never chose - and offers no other panel action.
 * A change requested later, by a user who already works with the panel, belongs to
 * {@link PasswordChangeController}.
 */
@Controller
class InitialPasswordChangeController {

    private static final String INITIAL_PASSWORD_CHANGE_VIEW = "authentication/initial-password-change";

    /**
     * Model attribute holding the form backing object. The controller publishes an empty instance both
     * when opening the screen and after a failure, so a submitted password is never sent back.
     */
    private static final String FORM_ATTRIBUTE = "initialPasswordChangeRequest";

    private final PanelUserService panelUserService;

    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * Creates the controller delegating credential changes to the panel-user service.
     *
     * @param panelUserService
     *         service owning password validation and persistence
     */
    InitialPasswordChangeController( @Nonnull PanelUserService panelUserService ) {
        this.panelUserService = requireNonNull( panelUserService, "panelUserService must not be null" );
    }

    /**
     * Displays an empty initial-password replacement form.
     *
     * @param model
     *         MVC model receiving the empty form
     * @return initial-password replacement template name
     */
    @GetMapping( "/initial-password-change" )
    @Nonnull
    String showInitialPasswordChange( @Nonnull Model model ) {
        model.addAttribute( FORM_ATTRIBUTE, emptyRequest() );
        return INITIAL_PASSWORD_CHANGE_VIEW;
    }

    /**
     * Validates and applies the initial password replacement, invalidating the authenticated session on
     * success so the user has to sign in with the password just chosen.
     *
     * @param initialPasswordChangeRequest
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
    @PostMapping( "/initial-password-change" )
    @Nonnull
    String changeInitialPassword(
            @Valid @ModelAttribute( FORM_ATTRIBUTE )
            @Nonnull InitialPasswordChangeRequest initialPasswordChangeRequest,
            @Nonnull BindingResult bindingResult,
            @Nonnull Authentication authentication,
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response ) {
        requireNonNull( initialPasswordChangeRequest, "initialPasswordChangeRequest must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );
        requireNonNull( authentication, "authentication must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( response, "response must not be null" );

        if ( bindingResult.hasErrors() ) {
            throw new PasswordChangeException( PasswordChangeMessage.INVALID_INPUT );
        }

        panelUserService.changeInitialPassword( authentication.getName(), initialPasswordChangeRequest );
        logoutHandler.logout( request, response, authentication );
        return "redirect:/login";
    }

    private @Nonnull InitialPasswordChangeRequest emptyRequest() {
        return new InitialPasswordChangeRequest( null, null );
    }
}