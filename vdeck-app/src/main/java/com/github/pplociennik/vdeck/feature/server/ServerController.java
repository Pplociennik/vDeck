package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.exc.ServerKillException;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@Log4j2
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

    /**
     * Admits a graceful stop from the launch-specific island form.
     *
     * @param launchId
     *         hidden launch identifier
     * @param redirectAttributes
     *         acceptance flash attributes
     * @return home redirect without waiting for process exit
     * @throws NullPointerException
     *         if an argument is null
     */
    @PostMapping( "/server/stop" )
    @Nonnull
    String stopServer( @RequestParam( defaultValue = "" ) @Nonnull String launchId,
                       @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );
        serverService.stop( requireNonNull( launchId, "launchId must not be null" ) );
        redirectAttributes.addFlashAttribute( "serverStopping", true );
        return HOME_REDIRECT;
    }

    /**
     * Admits a restart from the launch-specific island form: a graceful stop followed by a start once the
     * process exit is observed. A refused admission leaves this method as a
     * {@link com.github.pplociennik.vdeck.feature.server.exc.ServerRestartException}.
     *
     * @param launchId
     *         hidden launch identifier
     * @param redirectAttributes
     *         acceptance flash attributes
     * @return home redirect without waiting for the stop or the start
     * @throws NullPointerException
     *         if an argument is null
     */
    @PostMapping( "/server/restart" )
    @Nonnull
    String restartServer( @RequestParam( defaultValue = "" ) @Nonnull String launchId,
                          @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );
        serverService.restart( requireNonNull( launchId, "launchId must not be null" ) );
        redirectAttributes.addFlashAttribute( "serverRestarting", true );
        return HOME_REDIRECT;
    }

    /**
     * Presents data-loss risk without changing process state or copying a world.
     *
     * @param launchId
     *         selected launch identifier
     * @param model
     *         confirmation model
     * @return confirmation view
     * @throws NullPointerException
     *         if an argument is null
     * @throws ServerKillException
     *         if the launch is stale or not stoppable
     */
    @GetMapping( "/server/kill-confirmation" )
    @Nonnull
    String confirmKill( @RequestParam( defaultValue = "" ) @Nonnull String launchId, @Nonnull Model model ) {
        requireNonNull( launchId, "launchId must not be null" );
        requireNonNull( model, "model must not be null" );
        ServerStatus status = serverService.getStatus();
        log.debug( "Checking Kill confirmation availability in state {}", status.getState() );
        if ( !status.getState().isStoppable() || !launchId.equals( status.getLaunchId() ) ) {
            throw new ServerKillException( ServerMessage.KILL_REJECTED, null );
        }
        model.addAttribute( "launchId", launchId );
        return "server/kill-confirmation";
    }

    /**
     * Admits Kill only after a launch-specific risk confirmation.
     *
     * @param launchId
     *         identifier from the confirmation screen
     * @param confirmed
     *         checkbox value; only literal true grants permission
     * @param redirectAttributes
     *         acceptance flash attributes
     * @return home redirect without waiting for process exit
     * @throws NullPointerException
     *         if an argument is null
     */
    @PostMapping( "/server/kill" )
    @Nonnull
    String killServer( @RequestParam( defaultValue = "" ) @Nonnull String launchId,
                       @RequestParam( defaultValue = "false" ) @Nonnull String confirmed,
                       @Nonnull RedirectAttributes redirectAttributes ) {
        requireNonNull( redirectAttributes, "redirectAttributes must not be null" );
        requireNonNull( confirmed, "confirmed must not be null" );
        serverService.kill( requireNonNull( launchId, "launchId must not be null" ), "true".equals( confirmed ) );
        redirectAttributes.addFlashAttribute( "serverStopping", true );
        return HOME_REDIRECT;
    }

    /**
     * Restores management only after explicit selection of a saved server launch.
     *
     * @param launchId
     *         saved launch offered by the current runtime
     * @return home redirect
     * @throws NullPointerException
     *         if the identifier is null
     */
    @PostMapping( "/server/adopt" )
    @Nonnull
    String adoptServer( @RequestParam( defaultValue = "" ) @Nonnull String launchId ) {
        serverService.adopt( requireNonNull( launchId, "launchId must not be null" ) );
        return HOME_REDIRECT;
    }

    /**
     * Renders the shared island using the fresh status contributed by ServerStatusAdvice.
     *
     * @return shared island fragment, including CSRF-protected forms
     */
    @GetMapping( "/server/status" )
    @Nonnull
    String statusFragment() {
        return "common/island :: island";
    }
}
