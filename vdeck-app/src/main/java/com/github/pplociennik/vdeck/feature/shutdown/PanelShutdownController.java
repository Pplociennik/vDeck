package com.github.pplociennik.vdeck.feature.shutdown;

import com.github.pplociennik.vdeck.feature.shutdown.service.PanelShutdownService;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static java.util.Objects.requireNonNull;

/**
 * Serves the "Zamknij vDeck" action of the side menu: a confirmation screen and the request closing the panel.
 *
 * <p>The closing request changes state, so it is a {@code POST} protected by CSRF and, like every other path,
 * by the administrator role required in {@code SecurityConfiguration}. It answers with the same screen in its
 * closing state instead of redirecting, because the panel stops accepting requests right after it.
 */
@Controller
class PanelShutdownController {

    /**
     * Path of both the confirmation screen and the closing request.
     */
    private static final String SHUTDOWN_PATH = "/panel/shutdown";

    /**
     * Name of the template showing the confirmation or, after the request, the closing message.
     */
    private static final String SHUTDOWN_VIEW = "shutdown/panel-shutdown";

    /**
     * Model attribute telling the template to show the closing message instead of the confirmation.
     */
    static final String CLOSING_ATTRIBUTE = "closing";

    private final PanelShutdownService panelShutdownService;

    /**
     * Creates the controller.
     *
     * @param panelShutdownService
     *         service closing the panel
     * @throws NullPointerException
     *         if {@code panelShutdownService} is {@code null}
     */
    PanelShutdownController( @Nonnull PanelShutdownService panelShutdownService ) {
        this.panelShutdownService = requireNonNull( panelShutdownService, "panelShutdownService must not be null" );
    }

    /**
     * Displays the confirmation screen; nothing is closed yet.
     *
     * @param model
     *         view model receiving the screen state
     * @return panel shutdown template name
     * @throws NullPointerException
     *         if {@code model} is {@code null}
     */
    @GetMapping( SHUTDOWN_PATH )
    @Nonnull
    String confirmShutdown( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        model.addAttribute( CLOSING_ATTRIBUTE, false );
        return SHUTDOWN_VIEW;
    }

    /**
     * Starts closing the panel and displays the closing message. The panel applies the configured close
     * strategy to the server while it shuts down, so the process may still run for minutes afterwards.
     *
     * @param model
     *         view model receiving the screen state
     * @return panel shutdown template name
     * @throws NullPointerException
     *         if {@code model} is {@code null}
     */
    @PostMapping( SHUTDOWN_PATH )
    @Nonnull
    String shutDown( @Nonnull Model model ) {
        requireNonNull( model, "model must not be null" );
        panelShutdownService.requestShutdown();
        model.addAttribute( CLOSING_ATTRIBUTE, true );
        return SHUTDOWN_VIEW;
    }
}
