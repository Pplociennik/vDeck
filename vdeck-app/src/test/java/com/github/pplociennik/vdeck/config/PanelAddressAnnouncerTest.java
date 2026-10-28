package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.ConsoleMessage;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * Verifies the panel address shown in the console and opened in the browser after start-up.
 */
@ExtendWith( MockitoExtension.class )
class PanelAddressAnnouncerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private UserConsole console;

    @Mock
    private SystemBrowser browser;

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
        underTest = new PanelAddressAnnouncer( environment, systemPropertiesService, console, browser );
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
     * Verifies that an IPv6 literal is bracketed in the URL.
     */
    @Test
    void shouldBracketIpv6_whenAddressIsIpv6() {
        // GIVEN
        environment.setProperty( "server.address", "::1" );

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "[::1]", 8085 );
    }

    /**
     * Verifies that a panel without an explicit address, as started from an IDE, shows localhost.
     */
    @Test
    void shouldShowLocalhost_whenAddressIsNotSet() {
        // GIVEN
        // No additional setup.

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "localhost", 8085 );
    }

    /**
     * Verifies that a wildcard listen address, which no browser can open, is shown as localhost.
     *
     * @param address
     *         IPv4 or IPv6 wildcard address
     */
    @ParameterizedTest( name = "[{index}] address={0} should be shown as localhost" )
    @ValueSource( strings = { "0.0.0.0", "::" } )
    void shouldShowLocalhost_whenAddressIsWildcard( String address ) {
        // GIVEN
        environment.setProperty( "server.address", address );

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "localhost", 8085 );
    }

    /**
     * Verifies that a host name is shown unchanged and not treated as an IP literal.
     */
    @Test
    void shouldShowHostName_whenAddressIsHostName() {
        // GIVEN
        environment.setProperty( "server.address", "vdeck-host" );

        // WHEN
        underTest.announce();

        // THEN
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "vdeck-host", 8085 );
    }

    /**
     * Verifies that the panel address is opened in the browser when the launcher requests it and the user
     * setting allows it.
     */
    @Test
    void shouldOpenBrowser_whenLauncherRequestsItAndSettingAllows() throws IOException {
        // GIVEN
        environment.setProperty( "server.address", "127.0.0.1" );
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "true" );

        // WHEN
        underTest.announce();

        // THEN
        then( browser ).should().open( URI.create( "http://127.0.0.1:8085" ) );
    }

    /**
     * Verifies that the user setting switched off keeps the browser closed despite the launcher's request.
     */
    @Test
    void shouldNotOpenBrowser_whenSettingIsOff() {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "false" );

        // WHEN
        underTest.announce();

        // THEN
        then( browser ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that an invalid user setting falls back to its default and opens the browser.
     */
    @Test
    void shouldOpenBrowser_whenSettingIsInvalid() throws IOException {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "yes" );

        // WHEN
        underTest.announce();

        // THEN
        then( browser ).should().open( URI.create( "http://localhost:8085" ) );
    }

    /**
     * Verifies that no browser is opened without the launcher's request, as in IDE runs and tests, and
     * that the user setting is then not read at all.
     */
    @Test
    void shouldNotOpenBrowser_whenNotRequested() {
        // GIVEN
        // No additional setup.

        // WHEN
        underTest.announce();

        // THEN
        then( browser ).shouldHaveNoInteractions();
        then( systemPropertiesService ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that an explicitly disabled launcher request opens nothing.
     */
    @Test
    void shouldNotOpenBrowser_whenRequestIsFalse() {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "false" );

        // WHEN
        underTest.announce();

        // THEN
        then( browser ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a browser failure neither stops the announcement nor propagates to Spring.
     */
    @Test
    void shouldKeepAddressInConsole_whenBrowserCannotBeOpened() throws IOException {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "true" );
        willThrow( new IOException( "no handler" ) ).given( browser ).open( any() );

        // WHEN
        var exception = catchThrowable( () -> underTest.announce() );

        // THEN
        assertThat( exception ).isNull();
        then( console ).should().show( ConsoleMessage.PANEL_ADDRESS, "localhost", 8085 );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenBrowserSetting( String value ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_OPEN_BROWSER ) )
                .willReturn( value );
    }
}
