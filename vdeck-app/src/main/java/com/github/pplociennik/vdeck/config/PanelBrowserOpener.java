package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.shared.PropertyReader;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Opens the panel address in the default browser once the panel has started.
 *
 * <p>The browser opens only when the launcher sets {@link UpdateLayout#PANEL_OPEN_BROWSER_PROPERTY}, so IDE
 * runs and tests never open one, and the user has not switched {@link VdeckProperty#PANEL_OPEN_BROWSER} off.
 */
@Log4j2
@Component
class PanelBrowserOpener {

    private final Environment environment;

    private final SystemPropertiesService systemPropertiesService;

    private final SystemBrowser browser;

    /**
     * Creates the opener using the default Windows browser.
     *
     * @param environment
     *         environment providing {@code server.address}, {@code server.port} and the launcher's request
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties} with the user's browser setting
     */
    @Autowired
    PanelBrowserOpener( @Nonnull Environment environment, @Nonnull SystemPropertiesService systemPropertiesService ) {
        this( environment, systemPropertiesService, new SystemBrowser() );
    }

    /**
     * Creates the opener using the given browser.
     *
     * @param environment
     *         environment providing {@code server.address}, {@code server.port} and the launcher's request
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties} with the user's browser setting
     * @param browser
     *         browser opening the panel address
     */
    PanelBrowserOpener( @Nonnull Environment environment, @Nonnull SystemPropertiesService systemPropertiesService,
                        @Nonnull SystemBrowser browser ) {
        this.environment = requireNonNull( environment, "environment must not be null" );
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.browser = requireNonNull( browser, "browser must not be null" );
    }

    /**
     * Opens the panel address in the browser when the launcher requests it and the user allows it. A
     * browser that cannot be opened only logs a warning: the address stays in the console and the panel
     * keeps running.
     *
     * <p>Runs before {@link PanelAddressAnnouncer}, so its log lines do not push the address off the last
     * console line.
     */
    @Order( Ordered.HIGHEST_PRECEDENCE )
    @EventListener( ApplicationReadyEvent.class )
    void open() {
        if ( !shouldOpen() ) {
            return;
        }

        try {
            browser.open( PanelAddress.from( environment ).uri() );
            log.info( "Opened the panel address in the default browser" );
        } catch ( IOException exception ) {
            log.warn( "The default browser could not be opened; the address is shown in the console", exception );
        }
    }

    private boolean shouldOpen() {
        if ( !environment.getProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, Boolean.class, false ) ) {
            log.debug( "The launcher did not request opening the browser" );
            return false;
        }
        if ( !PropertyReader.readBoolean( systemPropertiesService, VdeckProperty.FILE_NAME,
                VdeckProperty.PANEL_OPEN_BROWSER, VdeckProperty.PANEL_OPEN_BROWSER.getDefaultValue() ) ) {
            log.info( "Opening the browser is switched off in {}", VdeckProperty.FILE_NAME );
            return false;
        }
        return true;
    }
}
