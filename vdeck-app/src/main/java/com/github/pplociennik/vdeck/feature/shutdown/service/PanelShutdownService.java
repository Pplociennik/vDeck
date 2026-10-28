package com.github.pplociennik.vdeck.feature.shutdown.service;

/**
 * Closes the panel application on the administrator's request from the side menu.
 *
 * <p>Closing the panel is the same shutdown as pressing CTRL+C in the panel console: the Spring context is
 * closed, so {@code ServerShutdownCoordinator} applies the configured {@code panel.close.strategy} to the
 * managed Valheim server (AD-031) before the panel process exits.
 */
public interface PanelShutdownService {

    /**
     * Starts closing the panel and returns at once, without waiting for the shutdown.
     *
     * <p>The shutdown runs on its own thread, so the request that asked for it still receives its response:
     * the web server stops gracefully and completes the requests already in progress. Closing may then block
     * that thread for minutes while the close strategy stops the server. Only the first call starts the
     * shutdown; later calls are ignored. The method is safe to call from several threads.
     */
    void requestShutdown();
}
