package com.github.pplociennik.vdeck.feature.players;

import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.Objects.requireNonNull;

/**
 * Answers a {@link PlayerListUpdateException} thrown while a player list is changed. The user is redirected
 * back to the list's panel with the message and, for an addition, the submitted entry as flash attributes,
 * so the entry is shown again and reloading the page never repeats the post.
 *
 * <p>A rejection is logged at {@code INFO} without a stack trace; a failure to read or write the file is
 * logged at {@code ERROR} with it. The advice takes precedence over the application-wide default handler.
 */
@Log4j2
@Order( Ordered.HIGHEST_PRECEDENCE )
@ControllerAdvice( assignableTypes = PlayerListController.class )
class PlayerListExceptionHandler {

    /**
     * Flash attribute carrying the translation key of the message shown on the panel.
     */
    static final String ERROR_KEY_ATTRIBUTE = "playerListErrorKey";

    /**
     * Sends the user back to the list's panel with the message and the submitted entry.
     *
     * @param exception
     *         change that was not stored
     * @param redirectAttributes
     *         attributes receiving the flashed form and message key
     * @return redirect to the list's panel
     * @throws NullPointerException
     *         if an argument is null
     */
    @ExceptionHandler( PlayerListUpdateException.class )
    @Nonnull
    String handleChangeNotStored( @Nonnull PlayerListUpdateException exception,
                                  @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        if ( exception.isFailure() ) {
            log.error( "Player list {} change failed: {}", exception.getPlayerList(), exception.getMessageKey(),
                    exception );
        } else {
            log.info( "Player list {} change rejected: {}", exception.getPlayerList(), exception.getMessageKey() );
        }
        if ( exception.getSubmittedForm() != null ) {
            redirectAttributes.addFlashAttribute( exception.getSubmittedForm() );
        }
        redirectAttributes.addFlashAttribute( ERROR_KEY_ATTRIBUTE, exception.getMessageKey().getKey() );
        return "redirect:" + exception.getPlayerList().getPath();
    }
}
