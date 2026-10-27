package com.github.pplociennik.vdeck.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the launcher command line.
 */
class LauncherArgumentsTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private LauncherArguments underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the regular start by the start script, which forwards panel arguments.
     */
    @Test
    void shouldParsePanelArguments_whenStartedByScript() {
        // GIVEN
        String[] arguments = { "--", "--debug", "--server.port=1" };

        // WHEN
        underTest = LauncherArguments.parse( arguments );

        // THEN
        assertThat( underTest.afterUpdate() ).isFalse();
        assertThat( underTest.handoffId() ).isEmpty();
        assertThat( underTest.panelArguments() ).containsExactly( "--debug", "--server.port=1" );
    }

    /**
     * Verifies the start by the updater after an update.
     */
    @Test
    void shouldParseHandoff_whenStartedByUpdater() {
        // GIVEN
        String[] arguments = { "--after-update", "--handoff", "id", "--" };

        // WHEN
        underTest = LauncherArguments.parse( arguments );

        // THEN
        assertThat( underTest.afterUpdate() ).isTrue();
        assertThat( underTest.handoffId() ).contains( "id" );
        assertThat( underTest.panelArguments() ).isEmpty();
    }

    /**
     * Verifies that unknown, repeated or incomplete options are rejected.
     *
     * @param invalid
     *         space-separated invalid command line
     */
    @ParameterizedTest( name = "[{index}] arguments={0}" )
    @ValueSource( strings = { "--unknown", "--after-update --after-update", "--handoff", "--handoff a --handoff b" } )
    void shouldThrowException_whenCommandLineIsInvalid( String invalid ) {
        // GIVEN
        String[] arguments = invalid.split( " " );

        // WHEN
        var failure = catchThrowable( () -> LauncherArguments.parse( arguments ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that a null array is rejected.
     */
    @Test
    void shouldThrowException_whenArgumentsAreNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> LauncherArguments.parse( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
