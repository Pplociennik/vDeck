package com.github.pplociennik.vdeck.feature.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.service.LaunchCommandService;
import com.github.pplociennik.vdeck.feature.server.service.ServerLogService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies start orchestration without creating processes or touching installation files. */
@ExtendWith( MockitoExtension.class )
class ServerServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerRuntime serverRuntime;

    @Mock
    private LaunchProfileService launchProfileService;

    @Mock
    private LaunchCommandService launchCommandService;

    @Mock
    private ServerLogService serverLogService;

    @Mock
    private ServerStartValidator validator;

    @Mock
    private PathService pathService;

    @TempDir
    private Path directory;

    private ServerServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates the service with strict mocks and no shared stubbing. */
    @BeforeEach
    void setUp() {
        underTest = new ServerServiceImpl( serverRuntime, launchProfileService, launchCommandService,
                serverLogService, validator, pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies early rejection before settings or filesystem services are used. */
    @Test
    void shouldRejectStart_whenServerIsAlreadyRunning() {
        // GIVEN
        given( serverRuntime.status() ).willReturn( runningStatus() );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertRejected( thrown, ServerMessage.ALREADY_RUNNING );
        verifyNoInteractions( launchProfileService, launchCommandService, serverLogService, validator, pathService );
        verify( serverRuntime, never() ).start( any() );
    }

    /** Verifies that a concurrent accepted start remains a rejection rather than a technical failure. */
    @Test
    void shouldRejectStart_whenRuntimeWinsRace() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( command ) ).willThrow( new IllegalStateException( "Busy" ) );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertRejected( thrown, ServerMessage.ALREADY_RUNNING );
    }

    /** Verifies that a process creation failure retains the original cause for diagnosis. */
    @Test
    void shouldFailStart_whenRuntimeCannotCreateProcess() {
        // GIVEN
        var command = givenPreparedCommand();
        var cause = new UncheckedIOException( new IOException( "Cannot create process" ) );
        given( serverRuntime.start( command ) ).willThrow( cause );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( ServerStartException.class, failure -> {
            assertThat( failure.isFailure() ).isTrue();
            assertThat( failure.getMessageKey() ).isEqualTo( ServerMessage.START_FAILED );
            assertThat( failure ).hasCause( cause );
        } );
    }

    /** Verifies that log reservation failure prevents command construction and process creation. */
    @Test
    void shouldRejectStart_whenLogDirectoryIsUnusable() {
        // GIVEN
        givenValidatedProfile();
        given( serverLogService.createLogFileForNewStart() )
                .willThrow( new UncheckedIOException( new IOException( "Unwritable" ) ) );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertRejected( thrown, ServerMessage.LOG_DIRECTORY_NOT_WRITABLE );
        verifyNoInteractions( launchCommandService );
        verify( serverRuntime, never() ).start( any() );
    }

    /** Verifies that validation rejects a request before log reservation, script writing or launch. */
    @Test
    void shouldAvoidSideEffects_whenValidationRejectsProfile() {
        // GIVEN
        var profile = givenValidatedProfile();
        var rejection = ServerStartException.rejected( ServerMessage.EXECUTABLE_MISSING );
        doThrow( rejection ).when( validator ).validate( profile, directory.resolve( "valheim_server.exe" ) );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertThat( thrown ).isSameAs( rejection );
        verifyNoInteractions( serverLogService, launchCommandService );
        verify( pathService, never() ).getAbsolutePath( any() );
        verify( serverRuntime, never() ).start( any() );
    }

    /** Verifies exact mirror content and absence of temporary artifacts after a successful launch. */
    @Test
    void shouldWriteMirrorScript_whenStartSucceeds() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( command ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        var script = directory.resolve( ServerService.START_SCRIPT_NAME );
        assertThat( script ).hasContent( "@echo off\r\nREM Test mirror\r\n" );
        assertThat( script.getParent() ).isDirectoryContaining( "glob:**/*.bat" )
                .isDirectoryNotContaining( "glob:**/*.tmp" );
    }

    /**
     * Verifies that a mirror directory blocked by a file does not prevent the real launch.
     *
     * @throws IOException
     *         if the blocking file cannot be created
     */
    @Test
    void shouldStartAnyway_whenMirrorScriptCannotBeWritten() throws IOException {
        // GIVEN
        var command = givenPreparedCommand();
        Files.createFile( directory.resolve( ".vDeck" ) );
        given( serverRuntime.start( command ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( serverRuntime ).start( command );
    }

    /** Verifies that the runtime receives exactly the specification built by the command service. */
    @Test
    void shouldHandBuiltSpecToRuntime_whenStartIsValidated() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( command ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( validator ).validate( new LaunchConfiguration( profile(), "" ),
                directory.resolve( "valheim_server.exe" ) );
        verify( serverRuntime ).start( command );
    }

    /** Verifies that consecutive reads expose current runtime state without a service cache. */
    @Test
    void shouldReturnRuntimeStatus_whenStatusIsRead() {
        // GIVEN
        var first = runningStatus();
        var second = ServerStatus.crashed( directory.resolve( "run.log" ) );
        given( serverRuntime.status() ).willReturn( first, second );

        // WHEN
        var firstResult = underTest.getStatus();
        var secondResult = underTest.getStatus();

        // THEN
        assertThat( firstResult ).isSameAs( first );
        assertThat( secondResult ).isSameAs( second );
    }

    /**
     * Verifies each mandatory constructor dependency independently.
     *
     * @param dependency
     *         dependency deliberately omitted
     */
    @ParameterizedTest( name = "[{index}] missing {0}" )
    @ValueSource( strings = { "serverRuntime", "launchProfileService", "launchCommandService",
            "serverLogService", "validator", "pathService" } )
    void shouldThrowException_whenDependencyIsNull( String dependency ) {
        // GIVEN
        // Exactly one dependency is omitted.

        // WHEN
        var thrown = catchThrowable( () -> new ServerServiceImpl(
                dependency.equals( "serverRuntime" ) ? null : serverRuntime,
                dependency.equals( "launchProfileService" ) ? null : launchProfileService,
                dependency.equals( "launchCommandService" ) ? null : launchCommandService,
                dependency.equals( "serverLogService" ) ? null : serverLogService,
                dependency.equals( "validator" ) ? null : validator,
                dependency.equals( "pathService" ) ? null : pathService ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( dependency );
    }

    /** Verifies settings changing during validation cannot mix the checked profile and launched password. */
    @Test
    void shouldCaptureConfigurationOnce_whenSettingsChangeDuringValidation() {
        // GIVEN
        var captured = new LaunchConfiguration( profile(), "FirstSyntheticSecret" );
        var replacement = new LaunchConfiguration( profile(), "SecondSyntheticSecret" );
        var available = new AtomicReference<>( captured );
        Path executable = directory.resolve( "valheim_server.exe" );
        Path log = directory.resolve( "run.log" );
        var spec = new LaunchSpec( executable, List.of( "-password", captured.password() ), Map.of(), directory, log );
        given( serverRuntime.status() ).willReturn( ServerStatus.stopped() );
        given( launchProfileService.getConfiguration() ).willAnswer( invocation -> available.get() );
        given( pathService.getBaseDirectory() ).willReturn( directory );
        doAnswer( invocation -> {
            available.set( replacement );
            return null;
        } ).when( validator ).validate( same( captured ),
                eq( executable ) );
        given( serverLogService.createLogFileForNewStart() ).willReturn( log );
        given( launchCommandService.build( same( captured ),
                eq( log ) ) ).willReturn( spec );
        given( launchCommandService.renderScript( spec ) ).willReturn( "@echo off\r\n" );
        given( pathService.getAbsolutePath( ServerService.START_SCRIPT_NAME ) )
                .willReturn( directory.resolve( ServerService.START_SCRIPT_NAME ) );
        given( serverRuntime.start( spec ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( launchProfileService ).getConfiguration();
        verify( launchCommandService ).build( same( captured ),
                eq( log ) );
        verify( serverRuntime ).start( same( spec ) );
        assertThat( available.get() ).isSameAs( replacement );
        assertThat( spec.arguments() ).contains( "FirstSyntheticSecret" ).doesNotContain( "SecondSyntheticSecret" );
    }

    /** Verifies the persisted mirror retains Unicode instead of failing the ASCII encoder. */
    @Test
    void shouldPersistUnicodeMirror_whenNamesContainPolishCharacters() {
        // GIVEN
        var spec = givenPreparedCommand();
        String content = "@echo off\r\nREM Za\u017c\u00f3\u0142\u0107\r\n";
        given( launchCommandService.renderScript( spec ) ).willReturn( content );
        given( serverRuntime.start( spec ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        assertThat( directory.resolve( ServerService.START_SCRIPT_NAME ) ).hasContent( content );
        verify( serverRuntime ).start( spec );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private LaunchConfiguration givenValidatedProfile() {
        var profile = new LaunchConfiguration( profile(), "" );
        given( serverRuntime.status() ).willReturn( ServerStatus.stopped() );
        given( launchProfileService.getConfiguration() ).willReturn( profile );
        given( pathService.getBaseDirectory() ).willReturn( directory );
        return profile;
    }

    private LaunchSpec givenPreparedCommand() {
        var profile = givenValidatedProfile();
        var logFile = directory.resolve( "run.log" );
        var spec = new LaunchSpec( directory.resolve( "valheim_server.exe" ), List.of( "-batchmode" ),
                Map.of(), directory, logFile );
        given( serverLogService.createLogFileForNewStart() ).willReturn( logFile );
        given( launchCommandService.build( profile, logFile ) ).willReturn( spec );
        given( launchCommandService.renderScript( spec ) ).willReturn( "@echo off\r\nREM Test mirror\r\n" );
        given( pathService.getAbsolutePath( ServerService.START_SCRIPT_NAME ) )
                .willReturn( directory.resolve( ServerService.START_SCRIPT_NAME ) );
        return spec;
    }

    private ServerStatus runningStatus() {
        return ServerStatus.running( 42L, Instant.EPOCH, directory.resolve( "run.log" ) );
    }

    private static LaunchProfile profile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", false, false, false, "",
                "valheim-data", 1800, 4, 7200, 43200, null, Map.of(), Set.of() );
    }

    private static void assertRejected( Throwable thrown, ServerMessage reason ) {
        assertThat( thrown ).isInstanceOfSatisfying( ServerStartException.class, rejection -> {
            assertThat( rejection.getMessageKey() ).isEqualTo( reason );
            assertThat( rejection.isFailure() ).isFalse();
            assertThat( rejection ).hasNoCause();
        } );
    }
}
