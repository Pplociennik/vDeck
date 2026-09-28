package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import jakarta.annotation.Nonnull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import static java.util.Objects.requireNonNull;

/**
 * Makes the current server status available to every rendered view, because the action island shows it
 * and the island is embedded on several screens.
 *
 * <p>Unlike the feature's other advice this one is deliberately not limited by {@code assignableTypes}:
 * it contributes a read-only model attribute rather than handling exceptions, and the island is not
 * owned by any single controller. Contributing it centrally is what keeps every other controller from
 * having to inject {@link ServerService} just to draw a header.
 *
 * <p>The status is read on each request and never cached. A snapshot taken earlier would be wrong as
 * soon as the server exited on its own.
 */
@ControllerAdvice
class ServerStatusAdvice {

    /**
     * Model attribute the island reads the server state from.
     */
    static final String SERVER_STATUS_ATTRIBUTE = "serverStatus";

    private final ServerService serverService;

    /**
     * Creates the model contributor using the current server status.
     *
     * @param serverService
     *         source of lifecycle snapshots
     * @throws NullPointerException
     *         if the service is null
     */
    ServerStatusAdvice( @Nonnull ServerService serverService ) {
        this.serverService = requireNonNull( serverService, "serverService must not be null" );
    }

    /**
     * Supplies the current lifecycle snapshot of the managed server.
     *
     * @return status rendered by the action island
     */
    @ModelAttribute( SERVER_STATUS_ATTRIBUTE )
    @Nonnull
    ServerStatus serverStatus() {
        return serverService.getStatus();
    }
}
