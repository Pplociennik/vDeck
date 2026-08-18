package com.github.pplociennik.vdeck.feature.home;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link HomeController} serves the home template of the panel.
 */
class HomeControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private HomeController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new HomeController();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the panel root resolves to the home template rather than to a missing view, which
     * is where Spring Security's saved-request routing sends an administrator after signing in.
     */
    @Test
    void shouldReturnHomeTemplate_whenPanelRootIsRequested() {
        // GIVEN
        // an authenticated administrator opening the panel root

        // WHEN
        String result = underTest.showHome();

        // THEN
        assertThat( result ).isEqualTo( "home/index" );
    }
}
