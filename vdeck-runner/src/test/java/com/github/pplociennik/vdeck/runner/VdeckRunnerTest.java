package com.github.pplociennik.vdeck.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.runtime.api.ControlSenderCommand;
import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Verifies the standalone runner translates helper requests without any panel startup dependency. */
@ExtendWith( MockitoExtension.class )
class VdeckRunnerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private Consumer< ProcessIdentity > sender;

    private VdeckRunner underTest;

    private ProcessIdentity identity;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates the runner with a fake native signaling boundary. */
    @BeforeEach
    void setUp() {
        underTest = new VdeckRunner( sender );
        identity = new ProcessIdentity( 42, Instant.parse( "2026-10-07T12:00:00Z" ),
                Path.of( "installation with spaces", "server.exe" ).toAbsolutePath() );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Rejects an absent native boundary before processing any command. */
    @Test
    void shouldRejectConstruction_whenSenderIsNull() {
        // GIVEN
        // No native boundary.
        // WHEN
        var failure = catchThrowable( () -> new VdeckRunner( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "controlSender" );
    }

    /** Sends the parsed identity once and reports successful delivery. */
    @Test
    void shouldReturnSuccess_whenSignalIsSent() {
        // GIVEN
        String[] arguments = ControlSenderCommand.arguments( identity ).toArray( String[]::new );
        // WHEN
        int result = underTest.run( arguments );
        // THEN
        assertThat( result ).isZero();
        verify( sender ).accept( identity );
    }

    /**
     * Rejects unsupported and malformed commands without invoking a native operation.
     *
     * @param arguments
     *         invalid helper invocation
     */
    @ParameterizedTest( name = "[{index}] invalid helper command {0}" )
    @MethodSource( "invalidCommands" )
    void shouldReturnInputError_whenCommandIsInvalid( String[] arguments ) {
        // GIVEN
        // Invalid command supplied by the source.
        // WHEN
        int result = underTest.run( arguments );
        // THEN
        assertThat( result ).isEqualTo( 2 );
        verifyNoInteractions( sender );
    }

    /** Converts native or identity failures into a nonzero helper result. */
    @Test
    void shouldReturnSignalError_whenSendingFails() {
        // GIVEN
        String[] arguments = ControlSenderCommand.arguments( identity ).toArray( String[]::new );
        willThrow( new IllegalStateException( "Synthetic identity mismatch" ) ).given( sender ).accept( identity );
        // WHEN
        int result = underTest.run( arguments );
        // THEN
        assertThat( result ).isEqualTo( 1 );
        verify( sender ).accept( identity );
    }

    /** Rejects null arrays without invoking the native sender. */
    @Test
    void shouldRejectNull_whenArgumentsAreMissing() {
        // GIVEN
        // Missing array.
        // WHEN
        var failure = catchThrowable( () -> underTest.run( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "arguments" );
        verifyNoInteractions( sender );
    }

    /** Rejects null at the public entry point before System.exit or native initialization. */
    @Test
    void shouldRejectNull_whenMainArgumentsAreMissing() {
        // GIVEN
        // Missing array.
        // WHEN
        var failure = catchThrowable( () -> VdeckRunner.main( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "arguments" );
        verifyNoInteractions( sender );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static Stream< Arguments > invalidCommands() {
        return Stream.of( new String[0], new String[] { "--server.port=8085" },
                new String[] { "other", ControlSenderCommand.MODE },
                new String[] { ControlSenderCommand.MODE },
                new String[] { ControlSenderCommand.MODE, "42", "invalid", "server.exe" },
                new String[] { ControlSenderCommand.MODE, null, "2026-10-07T12:00:00Z", "server.exe" } )
                .map( command -> Arguments.of( (Object) command ) );
    }
}
