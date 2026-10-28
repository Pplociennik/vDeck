package com.github.pplociennik.vdeck.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies how {@link PanelAddress} derives the URL of the panel from its listen address and port.
 */
class PanelAddressTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private MockEnvironment environment;

    private PanelAddress underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Prepares the environment of a panel on port 8085; each test reads its own address from it.
     */
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment().withProperty( "server.port", "8085" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an IPv4 address and the port form the URL unchanged.
     */
    @Test
    void shouldUseAddress_whenAddressIsIpv4() {
        // GIVEN
        environment.setProperty( "server.address", "127.0.0.1" );

        // WHEN
        underTest = PanelAddress.from( environment );

        // THEN
        assertThat( underTest ).isEqualTo( new PanelAddress( "127.0.0.1", 8085 ) );
        assertThat( underTest.uri() ).isEqualTo( URI.create( "http://127.0.0.1:8085" ) );
    }

    /**
     * Verifies that an IPv6 literal is bracketed in the URL.
     */
    @Test
    void shouldBracketIpv6_whenAddressIsIpv6() {
        // GIVEN
        environment.setProperty( "server.address", "::1" );

        // WHEN
        underTest = PanelAddress.from( environment );

        // THEN
        assertThat( underTest.uri() ).isEqualTo( URI.create( "http://[::1]:8085" ) );
    }

    /**
     * Verifies that a panel without an explicit address, as started from an IDE, uses localhost.
     */
    @Test
    void shouldUseLocalhost_whenAddressIsNotSet() {
        // GIVEN
        // No additional setup.

        // WHEN
        underTest = PanelAddress.from( environment );

        // THEN
        assertThat( underTest.host() ).isEqualTo( "localhost" );
    }

    /**
     * Verifies that a wildcard listen address, which no browser can open, becomes localhost.
     *
     * @param address
     *         IPv4 or IPv6 wildcard address
     */
    @ParameterizedTest( name = "[{index}] address={0} should become localhost" )
    @ValueSource( strings = { "0.0.0.0", "::" } )
    void shouldUseLocalhost_whenAddressIsWildcard( String address ) {
        // GIVEN
        environment.setProperty( "server.address", address );

        // WHEN
        underTest = PanelAddress.from( environment );

        // THEN
        assertThat( underTest.host() ).isEqualTo( "localhost" );
    }

    /**
     * Verifies that a host name is kept unchanged and not treated as an IP literal.
     */
    @Test
    void shouldKeepHostName_whenAddressIsHostName() {
        // GIVEN
        environment.setProperty( "server.address", "vdeck-host" );

        // WHEN
        underTest = PanelAddress.from( environment );

        // THEN
        assertThat( underTest.host() ).isEqualTo( "vdeck-host" );
    }

    /**
     * Verifies that a missing port is reported instead of producing a URL without one.
     */
    @Test
    void shouldThrowException_whenPortIsNotSet() {
        // GIVEN
        MockEnvironment withoutPort = new MockEnvironment();

        // WHEN
        var exception = catchThrowable( () -> PanelAddress.from( withoutPort ) );

        // THEN
        assertThat( exception ).isInstanceOf( IllegalStateException.class );
    }

    /**
     * Verifies that a null environment is rejected.
     */
    @Test
    void shouldThrowException_whenEnvironmentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var exception = catchThrowable( () -> PanelAddress.from( null ) );

        // THEN
        assertThat( exception )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "environment" );
    }

    /**
     * Verifies that a null host is rejected.
     */
    @Test
    void shouldThrowException_whenHostIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var exception = catchThrowable( () -> new PanelAddress( null, 8085 ) );

        // THEN
        assertThat( exception )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "host" );
    }
}
