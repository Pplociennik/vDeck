package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.feature.server.service.PanelCloseStrategy;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Panel close strategy {@code leave}: sends no signal and keeps the server running. Releasing the runtime
 * afterwards abandons an active Stop without terminating the process, whose token allows explicit adoption
 * by the next panel (AD-017).
 */
@Log4j2
@Component
class LeavePanelCloseStrategy implements PanelCloseStrategy {

    private final ServerRuntime runtime;

    /**
     * Creates the strategy.
     *
     * @param runtime
     *         owner of the managed server, read only to log what is left running
     */
    LeavePanelCloseStrategy( @Nonnull ServerRuntime runtime ) {
        this.runtime = requireNonNull( runtime, "runtime must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull PanelCloseMode getMode() {
        return PanelCloseMode.LEAVE;
    }

    /** Leaves the managed server as it is and only logs its state. */
    @Override
    public void apply() {
        log.info( "Panel closes with strategy '{}'; the server is left {}", getMode().getValue(),
                runtime.status().getState() );
    }
}
