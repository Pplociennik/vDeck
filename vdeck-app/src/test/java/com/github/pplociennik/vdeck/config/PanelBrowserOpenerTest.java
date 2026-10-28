package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Verifies when {@link PanelBrowserOpener} opens the panel address in the browser after start-up.
 */
@ExtendWith( MockitoExtension.class )
class PanelBrowserOpenerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private SystemBrowser browser;

    private MockEnvironment environment;

    private PanelBrowserOpener underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the opener for a panel on 127.0.0.1:8085.
     */
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment()
                .withProperty( "server.address", "127.0.0.1" )
                .withProperty( "server.port", "8085" );
        underTest = new PanelBrowserOpener( environment, systemPropertiesService, browser );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the panel address is opened when the launcher requests it and the user setting allows it.
     */
    @Test
    void shouldOpenBrowser_whenLauncherRequestsItAndSettingAllows() throws IOException {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "true" );

        // WHEN
        underTest.open();

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
        underTest.open();

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
        underTest.open();

        // THEN
        then( browser ).should().open( URI.create( "http://127.0.0.1:8085" ) );
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
        underTest.open();

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
        underTest.open();

        // THEN
        then( browser ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a browser failure does not propagate to Spring and stop the panel.
     */
    @Test
    void shouldNotThrow_whenBrowserCannotBeOpened() throws IOException {
        // GIVEN
        environment.setProperty( UpdateLayout.PANEL_OPEN_BROWSER_PROPERTY, "true" );
        givenBrowserSetting( "true" );
        willThrow( new IOException( "no handler" ) ).given( browser ).open( any() );

        // WHEN
        var exception = catchThrowable( () -> underTest.open() );

        // THEN
        assertThat( exception ).isNull();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenBrowserSetting( String value ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_OPEN_BROWSER ) )
                .willReturn( value );
    }
}
