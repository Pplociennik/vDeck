package com.github.pplociennik.vdeck.feature.server.service;

import jakarta.annotation.Nonnull;

/**
 * What the panel does with the managed Valheim server while it closes (AD-031). Exactly one implementation
 * exists for every {@link PanelCloseMode}; the shutdown coordinator selects it by the
 * {@code panel.close.strategy} setting and applies it before the runtime releases its resources.
 */
public interface PanelCloseStrategy {

    /**
     * Returns the setting value selecting this strategy.
     *
     * @return mode implemented by this strategy
     */
    @Nonnull
    PanelCloseMode getMode();

    /**
     * Applies the strategy to the managed server and blocks until its operations end. Every runtime
     * operation is bounded by its own five-minute deadline, and the wait for each one is bounded as well,
     * so panel shutdown cannot hang. A failure is logged and never thrown: the panel must still release its
     * resources, and a surviving process keeps its token for explicit adoption.
     */
    void apply();
}
