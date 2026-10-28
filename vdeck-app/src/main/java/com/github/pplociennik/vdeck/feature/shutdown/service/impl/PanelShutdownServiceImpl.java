package com.github.pplociennik.vdeck.feature.shutdown.service.impl;

import com.github.pplociennik.vdeck.feature.shutdown.service.PanelShutdownService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Hands the panel exit to its executor exactly once.
 *
 * <p>The exit itself - closing the Spring context and ending the process - and the thread it runs on are
 * supplied by {@code config.PanelShutdownConfiguration}, so this class holds only the once-only rule and can be
 * tested without ending the test process. The class is public because that configuration, in another package,
 * creates it.
 */
@Log4j2
public class PanelShutdownServiceImpl implements PanelShutdownService {

    private final Runnable panelExit;

    private final Executor shutdownExecutor;

    /**
     * Tells whether the shutdown was already started; set once and never cleared, because a panel that is
     * closing cannot be kept open again.
     */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    /**
     * Creates the service.
     *
     * @param panelExit
     *         action closing the Spring context and ending the panel process
     * @param shutdownExecutor
     *         executor running the exit away from the request thread
     * @throws NullPointerException
     *         if any argument is {@code null}
     */
    public PanelShutdownServiceImpl( @Nonnull Runnable panelExit, @Nonnull Executor shutdownExecutor ) {
        this.panelExit = requireNonNull( panelExit, "panelExit must not be null" );
        this.shutdownExecutor = requireNonNull( shutdownExecutor, "shutdownExecutor must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public void requestShutdown() {
        if ( !shutdownRequested.compareAndSet( false, true ) ) {
            log.debug( "Panel shutdown was already requested; the repeated request is ignored" );
            return;
        }

        log.info( "Panel shutdown requested from the side menu" );
        shutdownExecutor.execute( panelExit );
    }
}
