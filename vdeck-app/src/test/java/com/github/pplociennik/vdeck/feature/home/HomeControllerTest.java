package com.github.pplociennik.vdeck.feature.home;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.feature.releasenotes.service.ReleaseNotesService;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that {@link HomeController} serves the home template of the panel together with the change
 * lists waiting for acknowledgement.
 */
@ExtendWith( MockitoExtension.class )
class HomeControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ReleaseNotesService releaseNotesService;

    private HomeController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new HomeController( releaseNotesService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the panel root resolves to the home template rather than to a missing view, which
     * is where Spring Security's saved-request routing sends an administrator after signing in, and that
     * the template receives the change lists to show.
     */
    @Test
    void shouldReturnHomeTemplateWithReleaseNotes_whenPanelRootIsRequested() {
        // GIVEN
        var releaseNotes = List.of( new ReleaseNotes( new ReleaseVersion( 1, 0, 0 ), List.of() ) );
        given( releaseNotesService.findUnacknowledged() ).willReturn( releaseNotes );
        var model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showHome( model );

        // THEN
        assertThat( result ).isEqualTo( "home/index" );
        assertThat( model ).containsEntry( HomeController.RELEASE_NOTES_ATTRIBUTE, releaseNotes );
    }

    /**
     * Verifies that a missing model is rejected before the service is asked.
     */
    @Test
    void shouldThrowException_whenModelIsNull() {
        // GIVEN
        // no model

        // WHEN
        var exception = catchThrowable( () -> underTest.showHome( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "model" );
        verifyNoInteractions( releaseNotesService );
    }

    /**
     * Verifies that the controller cannot be created without the release notes service.
     */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no service

        // WHEN
        var exception = catchThrowable( () -> new HomeController( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "releaseNotesService" );
    }
}
