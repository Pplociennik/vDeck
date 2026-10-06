package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies that a launch description is complete, absolute and frozen once built, and that it never
 * renders the arguments — one of which is the server password.
 */
class LaunchSpecTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String PASSWORD = "verySecretPassword";

    private static final Path EXECUTABLE = Path.of( "C:\\Valheim\\valheim_server.exe" ).toAbsolutePath();

    private static final Path WORKING_DIRECTORY = Path.of( "C:\\Valheim" ).toAbsolutePath();

    private static final Path LOG_FILE = Path.of( "C:\\Valheim\\.vDeck\\log\\run.log" ).toAbsolutePath();

    private LaunchSpec underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Builds a launch description that carries a password, so the redaction rules can be checked.
     */
    @BeforeEach
    void setUp() {
        underTest = new LaunchSpec( EXECUTABLE, List.of( "-name", "vDeck", "-password", PASSWORD ),
                Map.of( "SteamAppId", "892970" ), WORKING_DIRECTORY, LOG_FILE );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every component is kept as given, so the launcher passes on exactly what the
     * application resolved.
     */
    @Test
    void shouldKeepEveryComponent_whenBuilt() {
        // GIVEN
        // a complete launch description

        // WHEN
        List< String > result = underTest.arguments();

        // THEN
        assertThat( result ).containsExactly( "-name", "vDeck", "-password", PASSWORD );
        assertThat( underTest.executable() ).isEqualTo( EXECUTABLE );
        assertThat( underTest.workingDirectory() ).isEqualTo( WORKING_DIRECTORY );
        assertThat( underTest.logFile() ).isEqualTo( LOG_FILE );
        assertThat( underTest.environment() ).containsEntry( "SteamAppId", "892970" );
    }

    /**
     * Verifies that the description never renders its arguments, because doing so would put the server
     * password into any log entry that mentions the launch.
     */
    @Test
    void shouldNotRenderPassword_whenConvertedToString() {
        // GIVEN
        // a launch description whose arguments include the password

        // WHEN
        String result = underTest.toString();

        // THEN
        assertThat( result ).doesNotContain( PASSWORD )
                .contains( "<redacted, 4 entries>" )
                .contains( EXECUTABLE.toString() )
                .contains( LOG_FILE.toString() );
    }

    /**
     * Verifies that the collections are copied on construction, so a caller mutating its own list
     * afterwards cannot change what will be launched.
     */
    @Test
    void shouldFreezeCollections_whenBuiltFromMutableInput() {
        // GIVEN
        List< String > mutableArguments = new ArrayList<>( List.of( "-nographics" ) );
        Map< String, String > mutableEnvironment = new HashMap<>( Map.of( "SteamAppId", "892970" ) );
        LaunchSpec spec = new LaunchSpec( EXECUTABLE, mutableArguments, mutableEnvironment, WORKING_DIRECTORY,
                LOG_FILE );

        // WHEN
        mutableArguments.add( "-injected" );
        mutableEnvironment.put( "INJECTED", "yes" );

        // THEN
        assertThat( spec.arguments() ).containsExactly( "-nographics" );
        assertThat( spec.environment() ).containsOnlyKeys( "SteamAppId" );
    }

    /**
     * Verifies that the returned argument list cannot be modified through the accessor.
     */
    @Test
    void shouldReturnUnmodifiableArguments_whenAccessed() {
        // GIVEN
        List< String > arguments = underTest.arguments();

        // WHEN
        Throwable thrown = catchThrowable( () -> arguments.add( "-injected" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }

    /**
     * Verifies that a relative executable is refused, because it would resolve against whatever working
     * directory the panel happened to be started from.
     */
    @Test
    void shouldThrowException_whenExecutableIsRelative() {
        // GIVEN
        Path relative = Path.of( "valheim_server.exe" );

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchSpec( relative, List.of(), Map.of(),
                WORKING_DIRECTORY, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "executable must be absolute" );
    }

    /**
     * Verifies that a relative log file is refused, so the server is never told to write somewhere
     * that depends on its own working directory.
     */
    @Test
    void shouldThrowException_whenLogFileIsRelative() {
        // GIVEN
        Path relative = Path.of( "run.log" );

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchSpec( EXECUTABLE, List.of(), Map.of(),
                WORKING_DIRECTORY, relative ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "logFile must be absolute" );
    }

    /**
     * Verifies that a null argument list is rejected rather than being treated as no arguments.
     */
    @Test
    void shouldThrowException_whenArgumentsAreNull() {
        // GIVEN
        // no argument list

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchSpec( EXECUTABLE, null, Map.of(),
                WORKING_DIRECTORY, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "arguments must not be null" );
    }

    /**
     * Verifies that a null environment is rejected, since the child process always needs at least the
     * Steam application identifier.
     */
    @Test
    void shouldThrowException_whenEnvironmentIsNull() {
        // GIVEN
        // no environment

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchSpec( EXECUTABLE, List.of(), null,
                WORKING_DIRECTORY, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "environment must not be null" );
    }

    /**
     * Verifies that a null executable is rejected before any path check runs.
     */
    @Test
    void shouldThrowException_whenExecutableIsNull() {
        // GIVEN
        // no executable

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchSpec( null, List.of(), Map.of(),
                WORKING_DIRECTORY, LOG_FILE ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "executable must not be null" );
    }
    /** Verifies that a relative working directory is rejected just like the executable and log paths. */
    @Test
    void shouldRejectSpecification_whenWorkingDirectoryIsRelative() {
        // GIVEN
        var original = underTest;

        // WHEN
        var thrown = catchThrowable( () -> new LaunchSpec( original.executable(), original.arguments(),
                original.environment(), Path.of( "relative" ), original.logFile() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "workingDirectory" );
    }

    /**
     * Verifies both remaining required paths reject null independently.
     *
     * @param missing
     *         path deliberately omitted
     */
    @ParameterizedTest( name = "[{index}] missing {0}" )
    @ValueSource( strings = { "workingDirectory", "logFile" } )
    void shouldRejectSpecification_whenRequiredPathIsNull( String missing ) {
        // GIVEN
        var original = underTest;

        // WHEN
        var thrown = catchThrowable( () -> new LaunchSpec( original.executable(), original.arguments(),
                original.environment(), missing.equals( "workingDirectory" ) ? null : original.workingDirectory(),
                missing.equals( "logFile" ) ? null : original.logFile() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( missing );
    }

    /** Verifies a null argument element cannot reach native command-line encoding. */
    @Test
    void shouldRejectSpecification_whenArgumentElementIsNull() {
        // GIVEN
        var original = underTest;

        // WHEN
        var thrown = catchThrowable( () -> new LaunchSpec( original.executable(), Arrays.asList( "-name", null ),
                original.environment(), original.workingDirectory(), original.logFile() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class );
    }

    /**
     * Verifies null environment keys and values are rejected before native block encoding.
     *
     * @param nullKey
     *         whether the key rather than its value is absent
     */
    @ParameterizedTest( name = "[{index}] null environment key={0}" )
    @ValueSource( booleans = { true, false } )
    void shouldRejectSpecification_whenEnvironmentContainsNull( boolean nullKey ) {
        // GIVEN
        var environment = new HashMap< String, String >();
        environment.put( nullKey ? null : "NAME", nullKey ? "value" : null );
        var original = underTest;

        // WHEN
        var thrown = catchThrowable( () -> new LaunchSpec( original.executable(), original.arguments(),
                environment, original.workingDirectory(), original.logFile() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class );
    }

    /** Verifies callers cannot mutate the environment after validation has captured it. */
    @Test
    void shouldRejectEnvironmentMutation_whenSpecificationIsPublished() {
        // GIVEN
        var environment = underTest.environment();

        // WHEN
        var thrown = catchThrowable( () -> environment.put( "INJECTED", "value" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
        assertThat( underTest.environment() ).doesNotContainKey( "INJECTED" );
    }

}
