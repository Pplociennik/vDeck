package com.github.pplociennik.vdeck.feature.releasenotes;

import com.github.pplociennik.vdeck.feature.releasenotes.exc.ReleaseNotesException;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.Objects.requireNonNull;

/**
 * Turns a failed change list acknowledgement into a message inside the still open panel on the home
 * page (AD-014). The exception is logged exactly once, here, with its stack trace.
 */
@Log4j2
@Order( Ordered.HIGHEST_PRECEDENCE )
@ControllerAdvice( assignableTypes = ReleaseNotesController.class )
class ReleaseNotesExceptionHandler {

    /**
     * Flash attribute holding the translation key of the reason the panel stays open.
     */
    static final String ERROR_KEY_ATTRIBUTE = "releaseNotesErrorKey";

    /**
     * View name sending the browser back to the panel's home page.
     */
    private static final String HOME_REDIRECT = "redirect:/";

    /**
     * Reports an acknowledgement that could not be stored and returns to the home page.
     *
     * @param exception
     *         process exception ending the acknowledgement
     * @param redirectAttributes
     *         attributes carrying the message across the redirect
     * @return redirect to the panel's home page
     * @throws NullPointerException
     *         if any argument is {@code null}
     */
    @ExceptionHandler( ReleaseNotesException.class )
    @Nonnull
    String handleAcknowledgementFailure( @Nonnull ReleaseNotesException exception,
                                         @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        log.error( "Release notes acknowledgement failed: {}", exception.getMessageKey(), exception );
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, exception.getMessageKey().getKey() );
        return HOME_REDIRECT;
    }
}
