package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static java.util.Objects.requireNonNull;

/**
 * Handles the server control actions of the action island. The island appears on several screens, so
 * every action ends by returning to the panel's home page with a flash message rather than rendering a
 * view of its own.
 *
 * <p>The endpoint changes state, so it is a {@code POST} protected by CSRF and, like every other path,
 * by the administrator role required in {@code SecurityConfiguration}.
 */
@Controller
class ServerController {

    /**
     * Flash attribute set after a start was accepted, which the home page turns into a confirmation.
     */
    static final String STARTED_ATTRIBUTE = "serverStarted";

    private static final String START_PATH = "/server/start";

    private static final String HOME_REDIRECT = "redirect:/";

    private final ServerService serverService;

    /**
     * Creates the controller delegating start requests to the server service.
     *
     * @param serverService
     *         application service controlling the server
     * @throws NullPointerException
     *         if the service is null
     */
    ServerController( @Nonnull ServerService serverService ) {
        this.serverService = requireNonNull( serverService, "serverService must not be null" );
    }

    /**
     * Starts the Valheim server described by the stored launch profile.
     *
     * <p>Returns as soon as the process exists, not when the server is ready for players, so the
     * confirmation says the server is starting rather than started. A refused or failed start leaves
     * this method as a {@link com.github.pplociennik.vdeck.feature.server.exc.ServerStartException},
     * which {@link ServerExceptionHandler} turns into a message on the same page.
     *
     * @param redirectAttributes
     *         attributes carrying the confirmation across the redirect
     * @return redirect to the panel's home page
     * @throws NullPointerException
     *         if {@code redirectAttributes} is {@code null}
     */
    @PostMapping( START_PATH )
    @Nonnull
    String startServer( @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );

        serverService.start();
        redirectAttributes.addFlashAttribute( STARTED_ATTRIBUTE, true );
        return HOME_REDIRECT;
    }
}
