package com.github.pplociennik.vdeck.feature.logs;

import com.github.pplociennik.vdeck.feature.logs.dto.LogChunk;
import com.github.pplociennik.vdeck.feature.logs.service.LogService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static java.util.Objects.requireNonNull;

/**
 * Serves the console screen, which shows the game server log of the current launch, and the endpoint
 * console.js polls for the lines written since its previous request.
 *
 * <p>Both paths only read, so they are plain {@code GET} requests; like every other path they require the
 * administrator role set in {@code SecurityConfiguration}.
 */
@Controller
class ConsoleController {

    private static final String CONSOLE_VIEW = "logs/console";

    private final LogService logService;

    /**
     * Creates the controller reading the log through the log service.
     *
     * @param logService
     *         service reading the current launch's log
     * @throws NullPointerException
     *         if the service is null
     */
    ConsoleController( @Nonnull LogService logService ) {
        this.logService = requireNonNull( logService, "logService must not be null" );
    }

    /**
     * Displays the console screen; its content is loaded by console.js.
     *
     * @return console template name
     */
    @GetMapping( "/console" )
    @Nonnull
    String showConsole() {
        return CONSOLE_VIEW;
    }

    /**
     * Returns the part of the current launch's log written since the given position.
     *
     * @param offset
     *         byte position returned by the previous poll, or {@code null} for the first poll
     * @return new log lines and the position to continue from
     */
    @GetMapping( "/console/log" )
    @ResponseBody
    @Nonnull
    LogChunk readLog( @RequestParam( required = false ) @Nullable Long offset ) {
        return logService.readCurrent( offset );
    }
}
