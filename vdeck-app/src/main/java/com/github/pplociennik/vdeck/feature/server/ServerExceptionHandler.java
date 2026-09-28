package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.Objects.requireNonNull;

/**
 * Turns a refused or failed server start into a message on the panel's home page (AD-014).
 *
 * <p>The exception is logged exactly once, here, and at a level that matches what happened: a
 * rejection is an ordinary outcome the administrator caused and can fix, while a failure is a defect
 * or an environment problem and keeps its stack trace.
 */
@Log4j2
@Order( Ordered.HIGHEST_PRECEDENCE )
@ControllerAdvice( assignableTypes = ServerController.class )
class ServerExceptionHandler {

    /**
     * Flash attribute holding the translation key of the reason a start did not happen.
     */
    static final String ERROR_KEY_ATTRIBUTE = "serverErrorKey";

    private static final String HOME_REDIRECT = "redirect:/";

    /**
     * Reports a start that did not happen and returns to the home page.
     *
     * @param exception
     *         process exception ending the start
     * @param redirectAttributes
     *         attributes carrying the message across the redirect
     * @return redirect to the panel's home page
     * @throws NullPointerException
     *         if any argument is {@code null}
     */
    @ExceptionHandler( ServerStartException.class )
    @Nonnull
    String handleStartNotPerformed( @Nonnull ServerStartException exception,
                                    @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        if ( exception.isFailure() ) {
            log.error( "Server start failed: {}", exception.getMessageKey(), exception );
        } else {
            log.info( "Server start rejected: {}", exception.getMessageKey() );
        }
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, exception.getMessageKey().getKey() );
        return HOME_REDIRECT;
    }
}