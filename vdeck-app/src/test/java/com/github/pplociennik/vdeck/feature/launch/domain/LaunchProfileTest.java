package com.github.pplociennik.vdeck.feature.launch.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link LaunchProfile} rejects missing components and freezes its collections.
 */
class LaunchProfileTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LaunchProfile underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that later changes of the collections passed in do not leak into the profile, and that
     * the profile's own collections cannot be modified.
     */
    @Test
    void shouldFreezeCollections_whenProfileIsCreated() {
        // GIVEN
        Map< WorldModifier, String > modifiers = new EnumMap<>( Map.of( WorldModifier.RAIDS, "none" ) );
        Set< WorldKey > keys = EnumSet.of( WorldKey.NO_MAP );

        // WHEN
        underTest = profile( modifiers, keys );
        modifiers.clear();
        keys.clear();

        // THEN
        assertThat( underTest.modifiers() ).containsExactly( Map.entry( WorldModifier.RAIDS, "none" ) );
        assertThat( underTest.enabledKeys() ).containsExactly( WorldKey.NO_MAP );
        assertThat( catchThrowable( () -> underTest.enabledKeys().add( WorldKey.NO_BUILD_COST ) ) )
                .isInstanceOf( UnsupportedOperationException.class );
    }

    /**
     * Verifies that a profile without its modifier map is rejected.
     */
    @Test
    void shouldThrowException_whenModifiersAreNull() {
        // GIVEN
        // no modifier map at all

        // WHEN
        Throwable thrown = catchThrowable( () -> profile( null, Set.of() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "modifiers must not be null" );
    }

    /**
     * Verifies that a profile without a server name is rejected.
     */
    @Test
    void shouldThrowException_whenServerNameIsNull() {
        // GIVEN
        // no server name at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchProfile( null, 2456, "Dedicated", false, true, false,
                "", "valheim-data", 1800, 4, 7200, 43200, null, Map.of(), Set.of() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "serverName must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static LaunchProfile profile( Map< WorldModifier, String > modifiers, Set< WorldKey > keys ) {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", false, true, false, "", "valheim-data", 1800, 4,
                7200, 43200, null, modifiers, keys );
    }
}