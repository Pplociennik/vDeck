package com.github.pplociennik.vdeck.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link PanelVersionAdvice} hands the build version to the side menu footer unchanged and
 * reports an unknown version when the build generated no build information.
 */
class PanelVersionAdviceTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private PanelVersionAdvice underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates fresh advice for a release build before each test. */
    @BeforeEach
    void setUp() {
        underTest = new PanelVersionAdvice( buildProperties( "0.1.0" ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that a release build shows its version. */
    @Test
    void shouldReturnBuildVersion_whenBuildPropertiesExist() {
        // GIVEN
        // advice created for version 0.1.0

        // WHEN
        var result = underTest.panelVersion();

        // THEN
        assertThat( result ).isEqualTo( "0.1.0" );
    }

    /** Verifies that a development build keeps its snapshot suffix, so the footer shows the version as built. */
    @Test
    void shouldKeepSnapshotSuffix_whenBuildIsDevelopmentBuild() {
        // GIVEN
        underTest = new PanelVersionAdvice( buildProperties( "0.1.0-SNAPSHOT" ) );

        // WHEN
        var result = underTest.panelVersion();

        // THEN
        assertThat( result ).isEqualTo( "0.1.0-SNAPSHOT" );
    }

    /** Verifies that the version is unknown, and the footer therefore hidden, without build information. */
    @Test
    void shouldReturnNull_whenBuildPropertiesAreMissing() {
        // GIVEN
        underTest = new PanelVersionAdvice( null );

        // WHEN
        var result = underTest.panelVersion();

        // THEN
        assertThat( result ).isNull();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static BuildProperties buildProperties( String version ) {
        Properties properties = new Properties();
        properties.setProperty( "version", version );
        return new BuildProperties( properties );
    }
}
