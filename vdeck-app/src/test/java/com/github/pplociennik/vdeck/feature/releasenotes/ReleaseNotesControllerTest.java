package com.github.pplociennik.vdeck.feature.releasenotes;

import com.github.pplociennik.vdeck.feature.releasenotes.exc.ReleaseNotesException;
import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link ReleaseNotesController} records the acknowledgement and returns to the home page.
 */
@ExtendWith( MockitoExtension.class )
class ReleaseNotesControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ReleaseNotesService releaseNotesService;

    private ReleaseNotesController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the controller over the mocked service.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseNotesController( releaseNotesService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that closing the panel stores the acknowledgement and redirects to the home page.
     */
    @Test
    void shouldAcknowledgeAndRedirectHome_whenPanelIsClosed() {
        // GIVEN
        // the panel shown on the home page

        // WHEN
        String result = underTest.acknowledge();

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        verify( releaseNotesService ).acknowledgeInstalled();
    }

    /**
     * Verifies that the controller does not catch the process exception, leaving it to the feature's advice.
     */
    @Test
    void shouldPropagateProcessException_whenAcknowledgementFails() {
        // GIVEN
        var failure = ReleaseNotesException.acknowledgementFailed( new IllegalStateException( "database locked" ) );
        willThrow( failure ).given( releaseNotesService ).acknowledgeInstalled();

        // WHEN
        var exception = catchThrowable( () -> underTest.acknowledge() );

        // THEN
        assertThat( exception ).isSameAs( failure );
    }

    /**
     * Verifies that the controller cannot be created without its service.
     */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no service

        // WHEN
        var exception = catchThrowable( () -> new ReleaseNotesController( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "releaseNotesService" );
    }
}
