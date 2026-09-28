package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** Verifies translation of process exceptions into safe flash messages. */
class ServerExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ServerExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates an independent exception handler. */
    @BeforeEach
    void setUp() {
        underTest = new ServerExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that a rejection carries its translation key across the redirect home. */
    @Test
    void shouldFlashReasonAndRedirect_whenStartRejected() {
        // GIVEN
        var rejection = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleStartNotPerformed( rejection, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.ALREADY_RUNNING.getKey() );
    }

    /** Verifies that a technical cause is not exposed to the browser. */
    @Test
    void shouldFlashReasonAndRedirect_whenStartFailed() {
        // GIVEN
        var failure = ServerStartException.startFailed( new IllegalStateException( "Technical failure" ) );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleStartNotPerformed( failure, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.START_FAILED.getKey() );
    }

    /** Verifies that missing exceptions are rejected before flash attributes are changed. */
    @Test
    void shouldThrowException_whenExceptionIsNull() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var thrown = catchThrowable( () -> underTest.handleStartNotPerformed( null, attributes ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "exception" );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** Verifies the non-null contract for flash storage. */
    @Test
    void shouldThrowException_whenRedirectAttributesAreNull() {
        // GIVEN
        var rejection = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );

        // WHEN
        var thrown = catchThrowable( () -> underTest.handleStartNotPerformed( rejection, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "redirectAttributes" );
    }
}
