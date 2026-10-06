package com.github.pplociennik.vdeck.runtime.windows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that arguments survive the encoding into the single command-line string
 * {@code CreateProcessW} requires, including the backslash and quote cases that a world name or a
 * server password can realistically contain.
 */
class WindowsCommandLineTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Path EXECUTABLE = Path.of( "C:\\Valheim\\valheim_server.exe" );

    private static final String QUOTED_EXECUTABLE = "\"C:\\Valheim\\valheim_server.exe\"";

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the executable is always quoted, so an installation path containing spaces stays
     * one argument instead of splitting into several.
     */
    @Test
    void shouldQuoteExecutable_whenPathContainsSpaces() {
        // GIVEN
        Path executable = Path.of( "C:\\Program Files\\Valheim\\valheim_server.exe" );

        // WHEN
        String result = WindowsCommandLine.build( executable, List.of() );

        // THEN
        assertThat( result ).isEqualTo( "\"C:\\Program Files\\Valheim\\valheim_server.exe\"" );
    }

    /**
     * Verifies that a command line without arguments consists of the quoted executable alone, with no
     * stray separator at the end.
     */
    @Test
    void shouldReturnOnlyExecutable_whenThereAreNoArguments() {
        // GIVEN
        // the executable alone

        // WHEN
        String result = WindowsCommandLine.build( EXECUTABLE, List.of() );

        // THEN
        assertThat( result ).isEqualTo( QUOTED_EXECUTABLE );
    }

    /**
     * Verifies that consecutive arguments are separated by exactly one space.
     */
    @Test
    void shouldSeparateArgumentsWithSingleSpace_whenSeveralAreGiven() {
        // GIVEN
        List< String > arguments = List.of( "-nographics", "-batchmode", "-port", "2456" );

        // WHEN
        String result = WindowsCommandLine.build( EXECUTABLE, arguments );

        // THEN
        assertThat( result ).isEqualTo( QUOTED_EXECUTABLE + " -nographics -batchmode -port 2456" );
    }

    /**
     * Verifies the encoding of a single argument for every case the Win32 rules distinguish: plain
     * values, whitespace, quotes and the backslash runs that precede a quote or end the value.
     *
     * @param argument
     *         raw argument value handed to the builder
     * @param expected
     *         encoding the child process parses back into the original value
     */
    @ParameterizedTest( name = "[{index}] argument <{0}> is encoded as <{1}>" )
    @MethodSource( "argumentEncodings" )
    void shouldEncodeArgument_whenItNeedsQuotingRules( String argument, String expected ) {
        // GIVEN
        // one argument described by the parameter set

        // WHEN
        String result = WindowsCommandLine.build( EXECUTABLE, List.of( argument ) );

        // THEN
        assertThat( result ).isEqualTo( QUOTED_EXECUTABLE + " " + expected );
    }

    /**
     * Verifies that a null executable is rejected before anything is built.
     */
    @Test
    void shouldThrowException_whenExecutableIsNull() {
        // GIVEN
        // no executable

        // WHEN
        Throwable thrown = catchThrowable( () -> WindowsCommandLine.build( null, List.of() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "executable must not be null" );
    }

    /**
     * Verifies that a null argument list is rejected before anything is built.
     */
    @Test
    void shouldThrowException_whenArgumentListIsNull() {
        // GIVEN
        // no argument list

        // WHEN
        Throwable thrown = catchThrowable( () -> WindowsCommandLine.build( EXECUTABLE, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "arguments must not be null" );
    }

    /**
     * Verifies that a null element is rejected and that the message names its position, so the caller
     * can find the argument that was not resolved.
     */
    @Test
    void shouldThrowException_whenArgumentListContainsNull() {
        // GIVEN
        List< String > arguments = Arrays.asList( "-name", null );

        // WHEN
        Throwable thrown = catchThrowable( () -> WindowsCommandLine.build( EXECUTABLE, arguments ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "arguments must not contain null at index 1" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Supplies the raw argument values together with the encoding {@code CommandLineToArgvW} parses
     * back into them.
     *
     * @return pairs of raw argument and expected encoding
     */
    private static Stream< Arguments > argumentEncodings() {
        return Stream.of(
                // A value without whitespace or quotes is passed through untouched.
                Arguments.of( "-batchmode", "-batchmode" ),
                Arguments.of( "2456", "2456" ),
                // Whitespace forces quoting, otherwise the value would split into several arguments.
                Arguments.of( "My server", "\"My server\"" ),
                Arguments.of( "tab\there", "\"tab\there\"" ),
                // An empty value would disappear entirely without explicit empty quotes.
                Arguments.of( "", "\"\"" ),
                // A quote inside the value is escaped with a backslash.
                Arguments.of( "say \"hi\"", "\"say \\\"hi\\\"\"" ),
                // Interior backslashes not touching a quote keep their count.
                Arguments.of( "C:\\worlds\\Dedicated", "C:\\worlds\\Dedicated" ),
                Arguments.of( "C:\\my worlds\\Dedicated", "\"C:\\my worlds\\Dedicated\"" ),
                // Backslashes directly before a quote are doubled, then the quote is escaped.
                Arguments.of( "ends\\\"quote", "\"ends\\\\\\\"quote\"" ),
                // Trailing backslashes are doubled so they cannot escape the closing quote.
                Arguments.of( "C:\\my worlds\\", "\"C:\\my worlds\\\\\"" ),
                Arguments.of( "two\\\\", "two\\\\" ),
                Arguments.of( "with space\\\\", "\"with space\\\\\\\\\"" ) );
    }
}