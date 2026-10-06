package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Verifies start delegation, confirmation and propagation of process exceptions to the advice.
 */
@ExtendWith( MockitoExtension.class )
class ServerControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerService serverService;

    private ServerController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a controller without starting a Spring context. */
    @BeforeEach
    void setUp() {
        underTest = new ServerController( serverService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that an accepted start returns home with confirmation in flash attributes. */
    @Test
    void shouldStartServerAndConfirm_whenRequestIsAccepted() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.startServer( attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes().get( "serverStarted" ) ).isEqualTo( true );
        verify( serverService ).start();
    }

    /** Verifies that a rejected start reaches the advice unchanged and produces no confirmation. */
    @Test
    void shouldNotCatchException_whenServiceRefuses() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();
        var rejection = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
        doThrow( rejection ).when( serverService ).start();

        // WHEN
        var thrown = catchThrowable( () -> underTest.startServer( attributes ) );

        // THEN
        assertThat( thrown ).isSameAs( rejection );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** Verifies that missing flash storage is rejected before the service changes state. */
    @Test
    void shouldThrowException_whenRedirectAttributesAreNull() {
        // GIVEN
        // No redirect attributes.

        // WHEN
        var thrown = catchThrowable( () -> underTest.startServer( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "redirectAttributes" );
        verifyNoInteractions( serverService );
    }

    /** Verifies that a controller cannot be constructed without its service. */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // No service.

        // WHEN
        var thrown = catchThrowable( () -> new ServerController( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "serverService" );
    }
}
