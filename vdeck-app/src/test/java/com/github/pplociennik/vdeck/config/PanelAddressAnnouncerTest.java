package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.BDDMockito.then;

/**
 * Verifies the panel address shown in the console after start-up.
 */
@ExtendWith( MockitoExtension.class )
class PanelAddressAnnouncerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private UserConsole console;

    private MockEnvironment environment;

    private PanelAddressAnnouncer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the announcer for a panel on port 8085.
     */
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment().withProperty( "server.port", "8085" );
        underTest = new PanelAddressAnnouncer( environment, console );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the configured address and port are shown together with the keep-open hint.
     */
    @Test
    void shouldShowAddress_whenPanelIsReady() {
        // GIVEN
        environment.setProperty( "server.address", "127.0.0.1" );

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "127.0.0.1", 8085 );
        then( console ).should().show( ConsoleMessage.KEEP_WINDOW_OPEN );
    }

    /**
     * Verifies that the console shows the URL host of {@link PanelAddress}, here the bracketed IPv6 literal.
     */
    @Test
    void shouldShowUrlHost_whenAddressIsIpv6() {
        // GIVEN
        environment.setProperty( "server.address", "::1" );

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "[::1]", 8085 );
    }
}
