package com.github.pplociennik.vdeck.feature.home;

import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import static java.util.Objects.requireNonNull;

/**
 * Serves the panel home page, the address a signed-in administrator lands on when no other request was
 * saved. The page carries the panel background, the action island and, after an update, the panel with
 * the change lists of the versions the administrator has not closed yet.
 */
@Controller
class HomeController {

    /**
     * Name of the home page template.
     */
    private static final String HOME_VIEW = "home/index";

    /**
     * Model attribute holding the change lists to show, newest version first; empty when the panel is hidden.
     */
    static final String RELEASE_NOTES_ATTRIBUTE = "releaseNotes";

    private final ReleaseNotesService releaseNotesService;

    /**
     * Creates the controller.
     *
     * @param releaseNotesService
     *         source of the change lists shown after an update
     * @throws NullPointerException
     *         if {@code releaseNotesService} is {@code null}
     */
    HomeController( @Nonnull ReleaseNotesService releaseNotesService ) {
        this.releaseNotesService = requireNonNull( releaseNotesService, "releaseNotesService must not be null" );
    }

    /**
     * Displays the panel home page together with the change lists waiting for acknowledgement.
     *
     * @param model
     *         view model receiving the change lists
     * @return home page template name
     * @throws NullPointerException
     *         if {@code model} is {@code null}
     */
    @GetMapping( "/" )
    @Nonnull
    String showHome( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        model.addAttribute( RELEASE_NOTES_ATTRIBUTE, releaseNotesService.findUnacknowledged() );
        return HOME_VIEW;
    }
}
