package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.exc.ServerAdoptException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerKillException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerRestartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStopException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.Objects.requireNonNull;

/**
 * Turns a refused or failed server control operation into a message on the panel's home page (AD-014).
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

    /**
     * Reports synchronous stop rejection on the island's home page.
     *
     * @param exception
     *         stop process exception
     * @param redirectAttributes
     *         safe error flash attributes
     * @return home redirect
     * @throws NullPointerException
     *         if an argument is null
     */
    @ExceptionHandler( ServerStopException.class )
    @Nonnull
    String handleStopNotPerformed( @Nonnull ServerStopException exception,
                                   @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        return reportControlFailure( exception.getMessageKey(), exception, redirectAttributes );
    }

    /**
     * Reports a refused or failed restart admission on the island's home page; the server keeps running.
     *
     * @param exception
     *         restart process exception
     * @param redirectAttributes
     *         safe error flash attributes
     * @return home redirect
     * @throws NullPointerException
     *         if an argument is null
     */
    @ExceptionHandler( ServerRestartException.class )
    @Nonnull
    String handleRestartNotPerformed( @Nonnull ServerRestartException exception,
                                      @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        return reportControlFailure( exception.getMessageKey(), exception, redirectAttributes );
    }

    /**
     * Reports rejected Kill permission or admission without performing force.
     *
     * @param exception
     *         Kill process exception
     * @param redirectAttributes
     *         safe error flash attributes
     * @param request
     *         submitted safe launch identifier and HTTP method
     * @return confirmation redirect for a submitted Kill form, otherwise home redirect
     * @throws NullPointerException
     *         if an argument is null
     */
    @ExceptionHandler( ServerKillException.class )
    @Nonnull
    String handleKillNotPerformed( @Nonnull ServerKillException exception,
                                   @Nonnull RedirectAttributes redirectAttributes,
                                   @Nonnull HttpServletRequest request ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( request, "request must not be null" );
        reportControlFailure( exception.getMessageKey(), exception, redirectAttributes );
        String launchId = request.getParameter( "launchId" );
        if ( "POST".equals( request.getMethod() ) && launchId != null && launchId.matches( "[A-Za-z0-9-]{1,100}" ) ) {
            return "redirect:/server/kill-confirmation?launchId=" + launchId;
        }
        return HOME_REDIRECT;
    }

    /**
     * Reports an adoption rejection or failure with its own translation key.
     *
     * @param exception
     *         adoption process exception
     * @param redirectAttributes
     *         safe error flash attributes
     * @return home redirect
     * @throws NullPointerException
     *         if an argument is null
     */
    @ExceptionHandler( ServerAdoptException.class )
    @Nonnull
    String handleAdoptNotPerformed( @Nonnull ServerAdoptException exception,
                                    @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        return reportControlFailure( exception.getMessageKey(), exception, redirectAttributes );
    }

    private String reportControlFailure( ServerMessage key, RuntimeException exception,
                                         RedirectAttributes redirectAttributes ) {
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );
        if ( exception.getCause() == null ) {
            log.info( "Server control request rejected: {}", key );
        } else {
            log.error( "Server control admission failed: {}", key, exception );
        }
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, key.getKey() );
        return HOME_REDIRECT;
    }
}
