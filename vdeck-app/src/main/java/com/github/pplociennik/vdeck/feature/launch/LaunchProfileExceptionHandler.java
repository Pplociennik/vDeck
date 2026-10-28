package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
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
 * Answers a {@link LaunchProfileUpdateException} thrown while one of the launch profile panels is
 * updated. The user is redirected back to the panel that was submitted, with the message and the
 * submitted form as flash attributes, so the edits are shown again and reloading the page never repeats
 * the post.
 *
 * <p>The form is flashed under its conventional attribute name (for example {@code serverAccessForm} or
 * {@code gameSettingsForm}), the one the panel's controller reads. A rejection is logged at
 * {@code INFO} without a stack trace; a failure to write the settings is logged at {@code ERROR} with it.
 * The advice takes precedence over the application-wide default handler.
 */
@Log4j2
@Order( Ordered.HIGHEST_PRECEDENCE )
@ControllerAdvice( assignableTypes = { ServerSettingsController.class, GameSettingsController.class } )
class LaunchProfileExceptionHandler {

    /**
     * Flash attribute carrying the translation key of the message shown on the screen.
     */
    static final String ERROR_KEY_ATTRIBUTE = "launchProfileErrorKey";

    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    /**
     * Sends the user back to the submitted panel with the message and the submitted values.
     *
     * @param exception
     *         update that was not stored
     * @param request
     *         request of the submitted panel, whose path is the redirect target
     * @param redirectAttributes
     *         attributes receiving the flashed form and message key
     * @return redirect to the submitted panel
     */
    @ExceptionHandler( LaunchProfileUpdateException.class )
    @Nonnull
    String handleUpdateNotStored( @Nonnull LaunchProfileUpdateException exception,
                                  @Nonnull HttpServletRequest request,
                                  @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        if ( exception.isFailure() ) {
            log.error( "Launch profile update failed: {}", exception.getMessageKey(), exception );
        } else {
            log.info( "Launch profile update rejected: {}", exception.getMessageKey() );
        }
        redirectAttributes.addFlashAttribute( exception.getSubmittedForm() );
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, exception.getMessageKey().getKey() );
        return "redirect:" + urlPathHelper.getPathWithinApplication( request );
    }
}
