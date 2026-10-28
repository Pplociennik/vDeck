package com.github.pplociennik.vdeck.feature.server.service.impl;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.feature.server.service.PanelCloseStrategy;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Panel close strategy {@code stop}, the default: stops the server gracefully and, when a live process
 * survives, forces it to exit after a snapshot attempt (AD-031). Closing the panel with this strategy is the
 * administrator's standing consent to that force; it never applies to a manual Stop.
 */
@Log4j2
@Component
class StopPanelCloseStrategy implements PanelCloseStrategy {

    private final ServerRuntime runtime;

    /**
     * Creates the strategy.
     *
     * @param runtime
     *         owner of the managed server
     */
    StopPanelCloseStrategy( @Nonnull ServerRuntime runtime ) {
        this.runtime = requireNonNull( runtime, "runtime must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull PanelCloseMode getMode() {
        return PanelCloseMode.STOP;
    }

    /**
     * Stops the managed server, forcing it only when graceful shutdown leaves a live process:
     *
     * <pre>
     * graceful shutdown (or the joined active Stop) ---&gt; STOPPED: done
     *          |
     *          v failed
     * state STOP_FAILED? -- no (timeout, interruption, no process) --&gt; nothing more
     *          | yes
     *          v
     * shutdown Kill: snapshot attempt -&gt; force -&gt; observed exit ---&gt; STOPPED
     *          |
     *          v failed
     * STOP_FAILED, process retained for explicit adoption
     * </pre>
     *
     * <p>Each of the two runtime operations has its own five-minute deadline and is awaited for at most
     * {@link StopCompletion#MAX_WAIT}. Process identity checks still apply to the force.
     */
    @Override
    public void apply() {
        log.info( "Panel closes with strategy '{}'; stopping the server", getMode().getValue() );
        if ( StopCompletion.await( "Graceful shutdown", runtime::shutdown, StopCompletion.MAX_WAIT ) ) {
            return;
        }

        ServerStatus status = runtime.status();
        if ( status.getState() != ServerState.STOP_FAILED ) {
            log.warn( "Server state after failed graceful shutdown is {}; force is not attempted", status.getState() );
            return;
        }

        log.warn( "Graceful shutdown left launch {} running; forcing it to exit", status.getLaunchId() );
        StopRequest kill = StopRequest.shutdownKill( status.getLaunchId() );
        if ( !StopCompletion.await( "Shutdown Kill", () -> runtime.stop( kill ), StopCompletion.MAX_WAIT ) ) {
            log.error( "Server state after failed shutdown Kill is {}; a surviving process is retained",
                    runtime.status().getState() );
        }
    }
}
