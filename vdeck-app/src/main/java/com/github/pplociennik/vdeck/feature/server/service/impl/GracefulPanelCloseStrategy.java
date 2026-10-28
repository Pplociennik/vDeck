package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.feature.server.service.PanelCloseStrategy;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Panel close strategy {@code graceful}: stops the server gracefully and never forces it. A process that
 * survives the graceful stop keeps running in {@code STOP_FAILED}, and its token allows explicit adoption by
 * the next panel (AD-017).
 */
@Log4j2
@Component
class GracefulPanelCloseStrategy implements PanelCloseStrategy {

    private final ServerRuntime runtime;

    /**
     * Creates the strategy.
     *
     * @param runtime
     *         owner of the managed server
     */
    GracefulPanelCloseStrategy( @Nonnull ServerRuntime runtime ) {
        this.runtime = requireNonNull( runtime, "runtime must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull PanelCloseMode getMode() {
        return PanelCloseMode.GRACEFUL;
    }

    /**
     * Gracefully stops, or joins the stop of, the managed server and waits for the outcome:
     *
     * <pre>
     * no managed server --------------------------&gt; nothing to do
     * snapshot -&gt; CTRL+C -&gt; one retry -&gt; exit ---&gt; STOPPED
     * any failure --------------------------------&gt; STOP_FAILED, process retained
     * </pre>
     */
    @Override
    public void apply() {
        log.info( "Panel closes with strategy '{}'; stopping the server gracefully", getMode().getValue() );
        if ( !StopCompletion.await( "Graceful shutdown", runtime::shutdown, StopCompletion.MAX_WAIT ) ) {
            log.warn( "Server state after failed graceful shutdown is {}; a surviving process is retained",
                    runtime.status().getState() );
        }
    }
}
