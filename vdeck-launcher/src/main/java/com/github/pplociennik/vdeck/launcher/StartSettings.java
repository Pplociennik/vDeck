package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.launcher.exc.InvalidStartSettingException;
import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Settings of the launcher in {@code .vDeck/config/vDeck-start.properties} (AD-026), replacing the
 * variables formerly edited in the start script.
 *
 * <ul>
 *     <li>{@code panel.address} — IPv4, IPv6 or host name the panel listens on, default {@code 127.0.0.1};</li>
 *     <li>{@code panel.port} — HTTP port 1–65535, default {@code 8085};</li>
 *     <li>{@code update.enabled} — {@code true} or {@code false}; with {@code false} the launcher never
 *     contacts GitHub and never installs updates, default {@code true}.</li>
 * </ul>
 *
 * <p>A missing key takes its default; an invalid value stops the start, because a mistyped address must
 * not silently expose the panel elsewhere. A missing file is created with the defaults.
 *
 * @param address
 *         listen address of the panel
 * @param port
 *         HTTP port of the panel
 * @param updateEnabled
 *         whether automatic updates are checked and installed at start
 */
@Log4j2
record StartSettings( @Nonnull String address, int port, boolean updateEnabled ) {

    /**
     * Key of the listen address.
     */
    static final String ADDRESS_KEY = "panel.address";

    /**
     * Key of the HTTP port.
     */
    static final String PORT_KEY = "panel.port";

    /**
     * Key of the automatic update switch.
     */
    static final String UPDATE_KEY = "update.enabled";

    /**
     * Default value of every key.
     */
    static final Map< String, String > DEFAULTS = Map.of( ADDRESS_KEY, "127.0.0.1", PORT_KEY, "8085", UPDATE_KEY,
            "true" );

    private static final int MAX_FILE_BYTES = 64 * 1024;

    private static final Pattern IPV4 = Pattern.compile( "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})" );

    private static final Pattern IPV6 = Pattern.compile( "[0-9A-Fa-f:.]{2,45}" );

    private static final Pattern HOST_NAME = Pattern.compile(
            "[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*" );

    /**
     * Contents of a missing file, equal to the distribution template so that comments and layout are the
     * same whichever of them created the file.
     */
    private static final String DEFAULT_FILE = """
            # vDeck start settings, read by the launcher at every start of vDeck.
            # Changes take effect at the next start.

            # ==================================================================================================
            # Panel address
            # ==================================================================================================

            # Network address the panel listens on: an IPv4 address, an IPv6 address or a host name.
            # - 127.0.0.1: the panel is reachable from this computer only (the safe default).
            # - LAN address of this computer: the panel is reachable from the local network.
            # - 0.0.0.0: the panel is reachable through every network interface of this computer.
            # Use anything other than 127.0.0.1 only on a trusted network: the panel serves plain HTTP.
            # An invalid value stops the start instead of exposing the panel elsewhere.
            panel.address=127.0.0.1

            # HTTP port of the panel, a number from 1 to 65535. Change it when another program already
            # uses 8085. The panel is then opened at http://<address>:<port>.
            panel.port=8085

            # ==================================================================================================
            # Updates
            # ==================================================================================================

            # Whether vDeck updates itself from its GitHub releases at start.
            # - true: at every start while the Valheim server is stopped, the launcher checks for a newer
            #   release, downloads and verifies it and installs it, keeping your settings.
            # - false: the launcher never contacts GitHub and never installs updates.
            update.enabled=true
            """;

    /**
     * Validates the settings.
     *
     * @throws NullPointerException
     *         if the address is null
     * @throws InvalidStartSettingException
     *         if the address or port is invalid
     */
    StartSettings {
        requireNonNull( address, "address must not be null" );
        if ( !isValidAddress( address ) ) {
            throw new InvalidStartSettingException( ADDRESS_KEY );
        }
        if ( port < 1 || port > 65_535 ) {
            throw new InvalidStartSettingException( PORT_KEY );
        }
    }

    /**
     * Reads the settings of an installation, creating the file with defaults when it is missing.
     *
     * @param installation
     *         canonical installation root
     * @return validated settings
     * @throws NullPointerException
     *         if the installation is null
     * @throws InvalidStartSettingException
     *         if a value is invalid
     * @throws IOException
     *         if the file is a link, too large, unreadable or cannot be created
     */
    static @Nonnull StartSettings load( @Nonnull Path installation ) throws IOException {
        requireNonNull( installation, "installation must not be null" );
        Path file = installation.resolve( DistributionItem.START_SETTINGS.getPath() );
        if ( !Files.exists( file, NOFOLLOW_LINKS ) ) {
            Files.createDirectories( file.getParent() );
            DurableFiles.write( file, DEFAULT_FILE.replace( "\n", "\r\n" ).getBytes( StandardCharsets.US_ASCII ) );
            log.info( "Created start settings with default values" );
        }
        Properties values = DurableFiles.readProperties( file, MAX_FILE_BYTES );
        StartSettings settings = new StartSettings( value( values, ADDRESS_KEY ),
                parsePort( value( values, PORT_KEY ) ), parseSwitch( value( values, UPDATE_KEY ) ) );
        log.info( "Start settings: port {}, automatic updates {}", settings.port, settings.updateEnabled );
        return settings;
    }

    private static String value( Properties values, String key ) {
        return values.getProperty( key, DEFAULTS.get( key ) ).strip();
    }

    private static int parsePort( String value ) {
        try {
            return Integer.parseInt( value );
        } catch ( NumberFormatException exception ) {
            throw new InvalidStartSettingException( PORT_KEY );
        }
    }

    private static boolean parseSwitch( String value ) {
        return switch ( value ) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new InvalidStartSettingException( UPDATE_KEY );
        };
    }

    private static boolean isValidAddress( String address ) {
        if ( address.isEmpty() || address.length() > 253 ) {
            return false;
        }
        Matcher ipv4 = IPV4.matcher( address );
        if ( ipv4.matches() ) {
            for ( int group = 1; group <= 4; group++ ) {
                if ( Integer.parseInt( ipv4.group( group ) ) > 255 ) {
                    return false;
                }
            }
            return true;
        }
        if ( address.contains( ":" ) ) {
            return IPV6.matcher( address ).matches();
        }
        boolean numericOnly = address.chars().allMatch( character -> character == '.' || Character.isDigit( character ) );
        return !numericOnly && HOST_NAME.matcher( address ).matches();
    }
}
