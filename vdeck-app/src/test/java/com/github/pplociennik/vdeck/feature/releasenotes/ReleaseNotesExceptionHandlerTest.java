package com.github.pplociennik.vdeck.feature.releasenotes;

import com.github.pplociennik.vdeck.feature.releasenotes.exc.ReleaseNotesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link ReleaseNotesExceptionHandler} turns a failed acknowledgement into a message on the
 * home page.
 */
class ReleaseNotesExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ReleaseNotesExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an independent exception handler.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseNotesExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the failure carries only its translation key across the redirect to the home page.
     */
    @Test
    void shouldFlashMessageAndRedirectHome_whenAcknowledgementFails() {
        // GIVEN
        var failure = ReleaseNotesException.acknowledgementFailed( new IllegalStateException( "database locked" ) );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleAcknowledgementFailure( failure, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( ReleaseNotesExceptionHandler.ERROR_KEY_ATTRIBUTE );
        assertThat( attributes.getFlashAttributes().get( ReleaseNotesExceptionHandler.ERROR_KEY_ATTRIBUTE ) )
                .isEqualTo( "releaseNotes.error.acknowledgementFailed" );
    }

    /**
     * Verifies that missing arguments are rejected before any flash attribute is stored.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        var failure = ReleaseNotesException.acknowledgementFailed( new IllegalStateException( "database locked" ) );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var exceptionFailure = catchThrowable( () -> underTest.handleAcknowledgementFailure( null, attributes ) );
        var attributesFailure = catchThrowable( () -> underTest.handleAcknowledgementFailure( failure, null ) );

        // THEN
        assertThat( exceptionFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "exception" );
        assertThat( attributesFailure ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "redirectAttributes" );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }
}
