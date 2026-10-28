package com.github.pplociennik.vdeck.feature.shutdown;

import com.github.pplociennik.vdeck.feature.shutdown.service.PanelShutdownService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that {@link PanelShutdownController} asks for confirmation without closing anything and closes the
 * panel only on the confirmed request.
 */
@ExtendWith( MockitoExtension.class )
class PanelShutdownControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private PanelShutdownService panelShutdownService;

    private PanelShutdownController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a fresh controller before each test. */
    @BeforeEach
    void setUp() {
        underTest = new PanelShutdownController( panelShutdownService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that opening the screen shows the confirmation and leaves the panel running. */
    @Test
    void shouldShowConfirmationWithoutClosing_whenScreenIsOpened() {
        // GIVEN
        var model = new ExtendedModelMap();

        // WHEN
        String result = underTest.confirmShutdown( model );

        // THEN
        assertThat( result ).isEqualTo( "shutdown/panel-shutdown" );
        assertThat( model ).containsEntry( PanelShutdownController.CLOSING_ATTRIBUTE, false );
        verifyNoInteractions( panelShutdownService );
    }

    /** Verifies that the confirmed request starts closing the panel and shows the closing message. */
    @Test
    void shouldRequestShutdownAndShowClosingMessage_whenShutdownIsConfirmed() {
        // GIVEN
        var model = new ExtendedModelMap();

        // WHEN
        String result = underTest.shutDown( model );

        // THEN
        assertThat( result ).isEqualTo( "shutdown/panel-shutdown" );
        assertThat( model ).containsEntry( PanelShutdownController.CLOSING_ATTRIBUTE, true );
        verify( panelShutdownService ).requestShutdown();
    }

    /** Verifies that the confirmation screen rejects a missing model. */
    @Test
    void shouldThrowException_whenConfirmationModelIsNull() {
        // GIVEN
        // no model

        // WHEN
        var exception = catchThrowable( () -> underTest.confirmShutdown( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "model" );
    }

    /** Verifies that a closing request without a model is rejected before the panel starts closing. */
    @Test
    void shouldThrowExceptionWithoutClosing_whenShutdownModelIsNull() {
        // GIVEN
        // no model

        // WHEN
        var exception = catchThrowable( () -> underTest.shutDown( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "model" );
        verifyNoInteractions( panelShutdownService );
    }

    /** Verifies that the controller cannot be created without the shutdown service. */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no service

        // WHEN
        var exception = catchThrowable( () -> new PanelShutdownController( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "panelShutdownService" );
    }
}
