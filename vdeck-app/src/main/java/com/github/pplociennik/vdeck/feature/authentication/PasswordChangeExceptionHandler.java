package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UrlPathHelper;

import static java.util.Objects.requireNonNull;

/**
 * Answers a {@link PasswordChangeException} thrown by either password-change screen. The user is
 * redirected back to the screen that was submitted with the message as a flash attribute; the form is
 * not flashed, because both screens always start with empty fields, so a submitted password is never
 * sent back. The rejection is logged at {@code DEBUG}, naming only the failed check.
 *
 * <p>The advice takes precedence over the application-wide default handler.
 */
@Log4j2
@Order( Ordered.HIGHEST_PRECEDENCE )
@ControllerAdvice( assignableTypes = { PasswordChangeController.class, InitialPasswordChangeController.class } )
class PasswordChangeExceptionHandler {

    /**
     * Flash attribute carrying the translation key of the message shown on the screen.
     */
    static final String ERROR_KEY_ATTRIBUTE = "passwordChangeErrorKey";

    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    /**
     * Sends the user back to the submitted screen with the message.
     *
     * @param exception
     *         rejected password change
     * @param request
     *         request of the submitted screen, whose path is the redirect target
     * @param redirectAttributes
     *         attributes receiving the flashed message key
     * @return redirect to the submitted screen
     */
    @ExceptionHandler( PasswordChangeException.class )
    @Nonnull
    String handleRejectedChange( @Nonnull PasswordChangeException exception,
                                 @Nonnull HttpServletRequest request,
                                 @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        log.debug( "Panel user password change was rejected: {}", exception.getMessageKey() );
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, exception.getMessageKey().getKey() );
        return "redirect:" + urlPathHelper.getPathWithinApplication( request );
    }
}
