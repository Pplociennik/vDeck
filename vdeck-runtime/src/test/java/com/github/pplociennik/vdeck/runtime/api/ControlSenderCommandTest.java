package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Instant;

/** Verifies technical argument validation without initializing native calls or a Spring context. */
class ControlSenderCommandTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private String[] underTest;

    private ProcessIdentity identity;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates helper arguments including spaces and non-ASCII characters in the executable path. */
    @BeforeEach
    void setUp() {
        identity = new ProcessIdentity( 42, Instant.parse( "2026-10-07T12:00:00Z" ),
                Path.of( "installation with spaces", "świat", "server.exe" ).toAbsolutePath().normalize() );
        underTest = ControlSenderCommand.arguments( identity ).toArray( String[]::new );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that separate arguments round-trip without shell quoting or loss of path characters. */
    @Test
    void shouldRecoverIdentity_whenArgumentsAreValid() {
        // GIVEN
        // Rendered arguments from setup.

        // WHEN
        var result = ControlSenderCommand.parse( underTest );

        // THEN
        assertThat( result ).isEqualTo( identity );
    }

    /** Verifies that a misplaced reserved flag is rejected before signaling. */
    @Test
    void shouldRejectCommand_whenFlagIsMisplaced() {
        // GIVEN
        underTest = new String[] { "other", ControlSenderCommand.MODE };

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies that ordinary panel arguments are not accepted as a signal command. */
    @Test
    void shouldRejectCommand_whenHelperFlagIsAbsent() {
        // GIVEN
        underTest = new String[] { "--server.port=8085" };

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies that invalid PID values never become usable identities.
     * @param pid
     *         malformed or reserved PID
     */
    @ParameterizedTest( name = "[{index}] invalid PID {0}" )
    @ValueSource( strings = { "", "text", "0", "-1", "4294967295", "9223372036854775808" } )
    void shouldRejectCommand_whenPidIsInvalid( String pid ) {
        // GIVEN
        underTest[1] = pid;

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies that malformed instants and relative executable paths are rejected independently.
     * @param position
     *         argument to invalidate
     */
    @ParameterizedTest( name = "[{index}] invalid identity component {0}" )
    @ValueSource( ints = { 2, 3 } )
    void shouldRejectCommand_whenIdentityIsMalformed( int position ) {
        // GIVEN
        underTest[position] = "invalid";

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies that each absent argument is rejected by the helper parser.
     * @param position
     *         missing argument index
     */
    @ParameterizedTest( name = "[{index}] null argument {0}" )
    @ValueSource( ints = { 0, 1, 2, 3 } )
    void shouldRejectCommand_whenArgumentIsNull( int position ) {
        // GIVEN
        underTest[position] = null;

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class );
    }

    /** Verifies that extra or missing arguments do not fall back to normal application startup. */
    @Test
    void shouldRejectCommand_whenArityIsWrong() {
        // GIVEN
        underTest = new String[] { ControlSenderCommand.MODE };

        // WHEN
        var thrown = catchThrowable( () -> ControlSenderCommand.parse( underTest ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Verifies nullability of all public command entry points. */
    @Test
    void shouldRejectNull_whenArgumentsOrIdentityAreMissing() {
        // GIVEN
        // Null arguments.

        // WHEN
        var parseFailure = catchThrowable( () -> ControlSenderCommand.parse( null ) );
        var renderFailure = catchThrowable( () -> ControlSenderCommand.arguments( null ) );

        // THEN
        assertThat( parseFailure ).isInstanceOf( NullPointerException.class );
        assertThat( renderFailure ).isInstanceOf( NullPointerException.class );
    }
}
