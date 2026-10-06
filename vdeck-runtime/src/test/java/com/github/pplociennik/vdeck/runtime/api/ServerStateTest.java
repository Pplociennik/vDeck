package com.github.pplociennik.vdeck.runtime.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies which lifecycle states allow a new server to be started. Getting this wrong in either
 * direction is serious: too permissive creates a second process against the same world, too strict
 * leaves the administrator with no way out of a failure.
 */
class ServerStateTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ServerState underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Selects the state a panel has before anything has been started.
     */
    @BeforeEach
    void setUp() {
        underTest = ServerState.STOPPED;
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the initial state permits a start.
     */
    @Test
    void shouldBeStartable_whenNothingHasBeenStartedYet() {
        // GIVEN
        // the state of a freshly created runtime

        // WHEN
        boolean result = underTest.isStartable();

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that every state in which no process is being managed permits a start, including the two
     * terminal failures, because relaunching is how an administrator recovers from them.
     *
     * @param state
     *         lifecycle state under test
     */
    @ParameterizedTest( name = "[{index}] {0} allows a start" )
    @EnumSource( value = ServerState.class, names = { "STOPPED", "START_FAILED", "CRASHED" } )
    void shouldBeStartable_whenNoProcessIsManaged( ServerState state ) {
        // GIVEN
        // a state that manages no process

        // WHEN
        boolean result = state.isStartable();

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that a start is refused while a process is already being created or is running, so two
     * servers can never exist at once.
     *
     * @param state
     *         lifecycle state under test
     */
    @ParameterizedTest( name = "[{index}] {0} refuses a start" )
    @EnumSource( value = ServerState.class, names = { "STARTING", "RUNNING" } )
    void shouldNotBeStartable_whenProcessIsAlreadyManaged( ServerState state ) {
        // GIVEN
        // a state that already owns a process

        // WHEN
        boolean result = state.isStartable();

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies the exact partition of the declared states into startable and blocked. Pinning both
     * sides means a state added later fails this test until someone decides which side it belongs to,
     * instead of silently defaulting to one.
     */
    @Test
    void shouldPartitionEveryDeclaredState_whenStartabilityIsDecided() {
        // GIVEN
        // every declared lifecycle state

        // WHEN
        List< ServerState > startable = Arrays.stream( ServerState.values() )
                .filter( ServerState::isStartable )
                .toList();
        List< ServerState > blocked = Arrays.stream( ServerState.values() )
                .filter( state -> !state.isStartable() )
                .toList();

        // THEN
        assertThat( startable ).containsExactly( ServerState.STOPPED, ServerState.START_FAILED,
                ServerState.CRASHED );
        assertThat( blocked ).containsExactly( ServerState.STARTING, ServerState.RUNNING );
    }
}