package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.RunLogRetention;
import com.github.pplociennik.vdeck.shared.LogRetention;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.log.PanelRunLog;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

import static java.util.Objects.requireNonNull;

/**
 * Removes panel run logs that fell out of the window configured by
 * {@link VdeckProperty#PANEL_LOG_RETENTION_ENABLED} and {@link VdeckProperty#PANEL_LOG_RETENTION_DAYS}
 * (AD-032), once per panel start.
 *
 * <p>It runs when the panel is ready: the settings are loaded and the panel holds the installation lock, so
 * no other run cleans the same directory. The log of the current run is dated today and is never removed.
 * A failure is logged and never stops the panel.
 */
@Log4j2
@Component
class PanelLogRetention {

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

    private final Clock clock;

    /**
     * Creates the retention using the system clock.
     *
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties}
     * @param pathService
     *         service resolving the installation directory
     */
    @Autowired
    PanelLogRetention( @Nonnull SystemPropertiesService systemPropertiesService, @Nonnull PathService pathService ) {
        this( systemPropertiesService, pathService, Clock.systemDefaultZone() );
    }

    /**
     * Creates the retention with an explicit clock for the current day.
     *
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties}
     * @param pathService
     *         service resolving the installation directory
     * @param clock
     *         clock and zone giving the current day
     * @throws NullPointerException
     *         if an argument is null
     */
    PanelLogRetention( @Nonnull SystemPropertiesService systemPropertiesService, @Nonnull PathService pathService,
                       @Nonnull Clock clock ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
        this.clock = requireNonNull( clock, "clock must not be null" );
    }

    /**
     * Deletes the day directories of panel run logs dated before the oldest kept day, unless retention is
     * switched off or set to {@code 0} days. Reads the settings and deletes files.
     */
    @EventListener( ApplicationReadyEvent.class )
    void apply() {
        LocalDate today = LocalDate.now( clock );
        LogRetention.oldestKeptDay( systemPropertiesService, VdeckProperty.PANEL_LOG_RETENTION_ENABLED,
                VdeckProperty.PANEL_LOG_RETENTION_DAYS, today ).ifPresent( oldestKept -> {
                    int deleted = RunLogRetention.deleteBefore( pathService.getBaseDirectory(), PanelRunLog.COMPONENT,
                            oldestKept );
                    log.info( "Removed {} panel run log directories dated before {}", deleted, oldestKept );
                } );
    }
}
