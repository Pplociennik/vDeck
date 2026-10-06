package com.github.pplociennik.vdeck.feature.launch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

/** Verifies the complete configuration's null contracts and protection against accidental logging. */
class LaunchConfigurationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LaunchProfile profile;

    private LaunchConfiguration underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates an immutable profile and a configuration with a synthetic password. */
    @BeforeEach
    void setUp() {
        profile = new LaunchProfile( "vDeck", 2456, "Dedicated", true, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200, null, Map.of(), Set.of() );
        underTest = new LaunchConfiguration( profile, "synthetic-password" );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that neither the password nor user-controlled profile fields appear in diagnostics. */
    @Test
    void shouldRedactConfiguration_whenConvertedToString() {
        // GIVEN
        // The configuration contains a synthetic secret.

        // WHEN
        var result = underTest.toString();

        // THEN
        assertThat( result ).isEqualTo( "LaunchConfiguration[<redacted>]" );
    }

    /** Verifies rejection of an absent profile before an incomplete configuration can be published. */
    @Test
    void shouldRejectConstruction_whenProfileIsNull() {
        // GIVEN
        // No profile was supplied.

        // WHEN
        var thrown = catchThrowable( () -> new LaunchConfiguration( null, "" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "profile" );
    }

    /** Verifies that absence of password protection must be expressed by an empty value, not null. */
    @Test
    void shouldRejectConstruction_whenPasswordIsNull() {
        // GIVEN
        // No password value was supplied.

        // WHEN
        var thrown = catchThrowable( () -> new LaunchConfiguration( profile, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "password" );
    }
}
