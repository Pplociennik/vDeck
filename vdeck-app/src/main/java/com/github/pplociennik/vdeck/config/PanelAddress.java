package com.github.pplociennik.vdeck.config;

import jakarta.annotation.Nonnull;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.URI;

import static java.util.Objects.requireNonNull;

/**
 * Address under which the user reaches the running panel, shared by the console announcement and the
 * browser so both always show the same URL.
 *
 * <p>An IPv6 literal is bracketed. A wildcard listen address ({@code 0.0.0.0}, {@code ::}) cannot be
 * opened, so it becomes {@code localhost}, as does a missing address in IDE runs.
 *
 * @param host
 *         host part of the URL, already bracketed for IPv6
 * @param port
 *         HTTP port of the panel
 */
record PanelAddress( @Nonnull String host, int port ) {

    /**
     * Host used when the panel listens on every interface or no address is configured.
     */
    private static final String DEFAULT_HOST = "localhost";

    /**
     * Creates the address.
     *
     * @param host
     *         host part of the URL, already bracketed for IPv6
     * @param port
     *         HTTP port of the panel
     * @throws NullPointerException
     *         if the host is null
     */
    PanelAddress {
        requireNonNull( host, "host must not be null" );
    }

    /**
     * Reads the address from {@code server.address} and {@code server.port}.
     *
     * @param environment
     *         environment of the started panel
     * @return address under which the user reaches the panel
     * @throws NullPointerException
     *         if the environment is null
     * @throws IllegalStateException
     *         if {@code server.port} is not set
     */
    static @Nonnull PanelAddress from( @Nonnull Environment environment ) {
        requireNonNull( environment, "environment must not be null" );
        String address = environment.getProperty( "server.address", DEFAULT_HOST );
        int port = environment.getRequiredProperty( "server.port", Integer.class );
        return new PanelAddress( urlHost( address ), port );
    }

    /**
     * Returns the HTTP URL of the panel.
     *
     * @return URL such as {@code http://127.0.0.1:8085}
     */
    @Nonnull
    URI uri() {
        return URI.create( "http://" + host + ":" + port );
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
}
