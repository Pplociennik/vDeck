package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.feature.server.exc.ServerRestartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Verifies start and restart delegation, confirmation and propagation of process exceptions to the advice.
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

    /** Verifies that an admitted restart returns home with its confirmation, without waiting for the stop. */
    @Test
    void shouldRestartServerAndConfirm_whenRequestIsAccepted() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.restartServer( "selected-launch", attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverRestarting" );
        assertThat( attributes.getFlashAttributes().get( "serverRestarting" ) ).isEqualTo( true );
        verify( serverService ).restart( "selected-launch" );
    }

    /** Verifies that a refused restart reaches the advice unchanged and produces no confirmation. */
    @Test
    void shouldNotCatchException_whenRestartIsRefused() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();
        var rejection = new ServerRestartException( ServerMessage.RESTART_REJECTED, null );
        doThrow( rejection ).when( serverService ).restart( "selected-launch" );

        // WHEN
        var thrown = catchThrowable( () -> underTest.restartServer( "selected-launch", attributes ) );

        // THEN
        assertThat( thrown ).isSameAs( rejection );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** Verifies that a restart without an identifier or flash storage is refused before the service is used. */
    @Test
    void shouldThrowException_whenRestartArgumentIsNull() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var noLaunch = catchThrowable( () -> underTest.restartServer( null, attributes ) );
        var noFlash = catchThrowable( () -> underTest.restartServer( "selected-launch", null ) );

        // THEN
        assertThat( noLaunch ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchId" );
        assertThat( noFlash ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "redirectAttributes" );
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
