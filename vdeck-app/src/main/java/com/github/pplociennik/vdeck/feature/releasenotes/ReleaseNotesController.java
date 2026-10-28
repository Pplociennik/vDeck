package com.github.pplociennik.vdeck.feature.releasenotes;

import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import static java.util.Objects.requireNonNull;

/**
 * Closes the change list panel shown on the home page after an update. The panel itself is rendered by
 * the home page, which asks {@link ReleaseNotesService} for the change lists to show.
 */
@Controller
class ReleaseNotesController {

    /**
     * View name sending the browser back to the panel's home page.
     */
    private static final String HOME_REDIRECT = "redirect:/";

    private final ReleaseNotesService releaseNotesService;

    /**
     * Creates the controller.
     *
     * @param releaseNotesService
     *         service recording the acknowledgement
     * @throws NullPointerException
     *         if {@code releaseNotesService} is {@code null}
     */
    ReleaseNotesController( @Nonnull ReleaseNotesService releaseNotesService ) {
        this.releaseNotesService = requireNonNull( releaseNotesService, "releaseNotesService must not be null" );
    }

    /**
     * Records that the administrator closed the change lists and returns to the home page, where the panel
     * is no longer shown.
     *
     * @return redirect to the panel's home page
     */
    @PostMapping( "/release-notes/acknowledge" )
    @Nonnull
    String acknowledge() {
        releaseNotesService.acknowledgeInstalled();
        return HOME_REDIRECT;
    }
}
