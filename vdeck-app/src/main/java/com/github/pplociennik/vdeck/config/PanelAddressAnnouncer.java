package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Shows the panel address in the console once the panel has started, so it is the last start-up line the
 * user sees instead of being buried under the start-up log.
 */
@Component
class PanelAddressAnnouncer {

    private static final String DEFAULT_HOST = "localhost";

    private final Environment environment;

    private final UserConsole console;

    /**
     * Creates the announcer writing to the standard console.
     *
     * @param environment
     *         environment providing {@code server.address} and {@code server.port}
     */
    @Autowired
    PanelAddressAnnouncer( @Nonnull Environment environment ) {
        this( environment, new UserConsole() );
    }

    /**
     * Creates the announcer writing to the given console.
     *
     * @param environment
     *         environment providing {@code server.address} and {@code server.port}
     * @param console
     *         console receiving the messages
     */
    PanelAddressAnnouncer( @Nonnull Environment environment, @Nonnull UserConsole console ) {
        this.environment = requireNonNull( environment, "environment must not be null" );
        this.console = requireNonNull( console, "console must not be null" );
    }

    /**
     * Shows the panel address and asks to keep the console window open.
     */
    @EventListener( ApplicationReadyEvent.class )
    void announce() {
        String address = environment.getProperty( "server.address", DEFAULT_HOST );
        String host = address.contains( ":" ) ? "[" + address + "]" : address;
        int port = environment.getRequiredProperty( "server.port", Integer.class );

        console.show( ConsoleMessage.PANEL_ADDRESS, host, port );
        console.show( ConsoleMessage.KEEP_WINDOW_OPEN );
    }
}
