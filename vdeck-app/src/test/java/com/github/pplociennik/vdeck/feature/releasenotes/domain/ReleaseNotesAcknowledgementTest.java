package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link ReleaseNotesAcknowledgement} stores the acknowledged version in its text form.
 */
class ReleaseNotesAcknowledgementTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ReleaseNotesAcknowledgement underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the acknowledgement of version 1.10.2.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseNotesAcknowledgement( new ReleaseVersion( 1, 10, 2 ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the stored key is the version text the service compares and the files are named by.
     */
    @Test
    void shouldStoreVersionText_whenCreated() {
        // GIVEN
        // the acknowledgement of version 1.10.2

        // WHEN
        String result = underTest.getVersion();

        // THEN
        assertThat( result ).isEqualTo( "1.10.2" );
    }

    /**
     * Verifies that an acknowledgement cannot be created without a version.
     */
    @Test
    void shouldThrowException_whenVersionIsNull() {
        // GIVEN
        // no version

        // WHEN
        var exception = catchThrowable( () -> new ReleaseNotesAcknowledgement( null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "version" );
    }
}
