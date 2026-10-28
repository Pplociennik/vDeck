package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import jakarta.annotation.Nonnull;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import static java.util.Objects.requireNonNull;

/**
 * Makes the current server status and the configured server name available to every rendered view,
 * because the action island shows both and the island is embedded on several screens.
 *
 * <p>Unlike the feature's other advice this one is deliberately not limited by {@code assignableTypes}:
 * it contributes a read-only model attribute rather than handling exceptions, and the island is not
 * owned by any single controller. Contributing it centrally is what keeps every other controller from
 * having to inject {@link ServerService} just to draw a header.
 *
 * <p>The status is read on each request and never cached. A snapshot taken earlier would be wrong as
 * soon as the server exited on its own. The name comes from the published launch profile, so a saved
 * server setting appears on the next render.
 */
@ControllerAdvice
class ServerStatusAdvice {

    /**
     * Model attribute the island reads the server state from.
     */
    static final String SERVER_STATUS_ATTRIBUTE = "serverStatus";

    /**
     * Model attribute the island reads the server name from.
     */
    static final String SERVER_NAME_ATTRIBUTE = "serverName";

    private final ServerService serverService;
    private final LaunchProfileService launchProfileService;

    /**
     * Creates the model contributor using the current server status and launch profile.
     *
     * @param serverService
     *         source of lifecycle snapshots
     * @param launchProfileService
     *         source of the configured server name
     * @throws NullPointerException
     *         if any service is null
     */
    ServerStatusAdvice( @Nonnull ServerService serverService, @Nonnull LaunchProfileService launchProfileService ) {
        this.serverService = requireNonNull( serverService, "serverService must not be null" );
        this.launchProfileService = requireNonNull( launchProfileService, "launchProfileService must not be null" );
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

    /**
     * Supplies the server name configured in the launch profile.
     *
     * @return name rendered above the action island
     */
    @ModelAttribute( SERVER_NAME_ATTRIBUTE )
    @Nonnull
    String serverName() {
        return launchProfileService.getProfile().serverName();
    }
}
