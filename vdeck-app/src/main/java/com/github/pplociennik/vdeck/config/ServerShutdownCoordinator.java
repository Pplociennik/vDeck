package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.feature.server.service.PanelCloseStrategy;
import com.github.pplociennik.vdeck.shared.PropertyReader;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Applies the panel close strategy chosen by {@link VdeckProperty#PANEL_CLOSE_STRATEGY} to the managed
 * Valheim server while the panel shuts down (AD-031).
 *
 * <p>The strategy runs in {@link PreDestroy}, after the web server has stopped accepting requests. Spring
 * destroys a bean before the beans it depends on, and every strategy depends on the server runtime, so the
 * runtime, its queue and the installation lock stay available until the strategy has finished; only then
 * does the runtime release its resources, without terminating a surviving process.
 */
@Log4j2
@Component
class ServerShutdownCoordinator {

    private final SystemPropertiesService systemPropertiesService;

    private final Map< PanelCloseMode, PanelCloseStrategy > strategies;

    /**
     * Creates the coordinator.
     *
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties} with the close strategy
     * @param strategies
     *         every panel close strategy, exactly one for each {@link PanelCloseMode}
     * @throws IllegalStateException
     *         if a mode has no strategy or more than one
     */
    ServerShutdownCoordinator( @Nonnull SystemPropertiesService systemPropertiesService,
                               @Nonnull List< PanelCloseStrategy > strategies ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.strategies = byMode( requireNonNull( strategies, "strategies must not be null" ) );
    }

    /**
     * Applies the configured strategy and blocks until it ends, which is bounded by the strategy's own
     * waits. A failing strategy is logged and never stops the remaining shutdown, because the runtime must
     * still release its resources.
     */
    @PreDestroy
    void closeServer() {
        PanelCloseMode mode = readMode();
        log.info( "Panel shutdown applies close strategy '{}'", mode.getValue() );
        try {
            strategies.get( mode ).apply();
        } catch ( RuntimeException exception ) {
            log.error( "Close strategy '{}' failed; the server state is left as it is", mode.getValue(), exception );
        }
    }

    private static Map< PanelCloseMode, PanelCloseStrategy > byMode( List< PanelCloseStrategy > strategies ) {
        Map< PanelCloseMode, PanelCloseStrategy > byMode = new EnumMap<>( PanelCloseMode.class );
        for ( PanelCloseStrategy strategy : strategies ) {
            if ( byMode.putIfAbsent( strategy.getMode(), strategy ) != null ) {
                throw new IllegalStateException( "More than one close strategy for mode " + strategy.getMode() );
            }
        }
        for ( PanelCloseMode mode : PanelCloseMode.values() ) {
            if ( !byMode.containsKey( mode ) ) {
                throw new IllegalStateException( "No close strategy for mode " + mode );
            }
        }
        return byMode;
    }

    /**
     * Reads the configured mode. An unknown value, or settings that cannot be read because the panel failed
     * to start, falls back to the default {@code stop}, the documented behavior for a missing setting.
     */
    private PanelCloseMode readMode() {
        PanelCloseMode defaultMode = PanelCloseMode.fromValue( VdeckProperty.PANEL_CLOSE_STRATEGY.getDefaultValue() )
                .orElseThrow();
        try {
            return PropertyReader.readOptional( systemPropertiesService, VdeckProperty.FILE_NAME,
                    VdeckProperty.PANEL_CLOSE_STRATEGY, PanelCloseMode::fromValue ).orElse( defaultMode );
        } catch ( RuntimeException exception ) {
            log.warn( "Close strategy could not be read; using the default '{}'", defaultMode.getValue(), exception );
            return defaultMode;
        }
    }
}
