package com.github.pplociennik.vdeck.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the updater command line.
 */
class UpdaterArgumentsTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private UpdaterArguments underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the recovery started by the start script, which forwards panel arguments.
     */
    @Test
    void shouldParseRecovery_whenStartedByScript() {
        // GIVEN
        String[] arguments = { "--recover", "--", "--debug", "--server.port=1" };

        // WHEN
        underTest = UpdaterArguments.parse( arguments );

        // THEN
        assertThat( underTest.recover() ).isTrue();
        assertThat( underTest.handoffId() ).isEmpty();
        assertThat( underTest.panelArguments() ).containsExactly( "--debug", "--server.port=1" );
    }

    /**
     * Verifies the apply mode started by the launcher.
     */
    @Test
    void shouldParseHandoff_whenStartedByLauncher() {
        // GIVEN
        String[] arguments = { "--handoff", "id", "--" };

        // WHEN
        underTest = UpdaterArguments.parse( arguments );

        // THEN
        assertThat( underTest.recover() ).isFalse();
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
    @ValueSource( strings = { "--unknown", "--recover --recover", "--handoff", "--handoff a --handoff b" } )
    void shouldThrowException_whenCommandLineIsInvalid( String invalid ) {
        // GIVEN
        String[] arguments = invalid.split( " " );

        // WHEN
        var failure = catchThrowable( () -> UpdaterArguments.parse( arguments ) );

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
        var failure = catchThrowable( () -> UpdaterArguments.parse( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
