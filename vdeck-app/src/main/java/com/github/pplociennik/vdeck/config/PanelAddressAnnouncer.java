package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import com.github.pplociennik.vdeck.shared.PropertyReader;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * Shows the panel address in the console once the panel has started, so it is the last start-up line the
 * user sees instead of being buried under the start-up log, and opens it in the default browser.
 *
 * <p>The browser opens only when the launcher sets {@link UpdateLayout#PANEL_OPEN_BROWSER_PROPERTY}, so IDE
 * runs and tests never open one, and the user has not switched {@link VdeckProperty#PANEL_OPEN_BROWSER} off.
 *
 * <p>A wildcard listen address ({@code 0.0.0.0}, {@code ::}) cannot be opened, so the address shown and
 * opened is then {@code localhost}.
 */
@Log4j2
@Component
class PanelAddressAnnouncer {

    /**
     * Host shown when the panel listens on every interface or no address is configured.
     */
    private static final String DEFAULT_HOST = "localhost";

    private final Environment environment;

    private final SystemPropertiesService systemPropertiesService;

    private final UserConsole console;

    private final SystemBrowser browser;

    /**
     * Creates the announcer writing to the standard console and opening the default Windows browser.
     *
     * @param environment
     *         environment providing {@code server.address}, {@code server.port} and the launcher's request
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties} with the user's browser setting
     */
    @Autowired
    PanelAddressAnnouncer( @Nonnull Environment environment, @Nonnull SystemPropertiesService systemPropertiesService ) {
        this( environment, systemPropertiesService, new UserConsole(), new SystemBrowser() );
    }

    /**
     * Creates the announcer writing to the given console and opening the given browser.
     *
     * @param environment
     *         environment providing {@code server.address}, {@code server.port} and the launcher's request
     * @param systemPropertiesService
     *         service holding the loaded {@code vDeck.properties} with the user's browser setting
     * @param console
     *         console receiving the messages
     * @param browser
     *         browser opening the panel address
     */
    PanelAddressAnnouncer( @Nonnull Environment environment, @Nonnull SystemPropertiesService systemPropertiesService,
                           @Nonnull UserConsole console, @Nonnull SystemBrowser browser ) {
        this.environment = requireNonNull( environment, "environment must not be null" );
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
        this.browser = requireNonNull( browser, "browser must not be null" );
    }

    /**
     * Shows the panel address, asks to keep the console window open and opens the address in the browser
     * when the launcher requests it and the user allows it. A browser that cannot be opened only logs a warning: the address stays in the
     * console and the panel keeps running.
     */
    @EventListener( ApplicationReadyEvent.class )
    void announce() {
        String host = urlHost( environment.getProperty( "server.address", DEFAULT_HOST ) );
        int port = environment.getRequiredProperty( "server.port", Integer.class );

        console.show( ConsoleMessage.PANEL_ADDRESS, host, port );
        console.show( ConsoleMessage.KEEP_WINDOW_OPEN );

        if ( shouldOpenBrowser() ) {
            openBrowser( URI.create( "http://" + host + ":" + port ) );
        }
    }

    private static String urlHost( String address ) {
        if ( isWildcard( address ) ) {
            return DEFAULT_HOST;
        }
        return address.contains( ":" ) ? "[" + address + "]" : address;
    }

    private static boolean isWildcard( String address ) {
        try {
            return InetAddress.ofLiteral( address ).isAnyLocalAddress();
        } catch ( IllegalArgumentException hostName ) {
            // A host name is no IP literal and therefore never the wildcard address.
            return false;
        }
    }

    private boolean shouldOpenBrowser() {
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

    private void openBrowser( URI address ) {
        try {
            browser.open( address );
            log.info( "Opened the panel address in the default browser" );
        } catch ( IOException exception ) {
            log.warn( "The default browser could not be opened; the address is shown in the console", exception );
        }
    }
}
