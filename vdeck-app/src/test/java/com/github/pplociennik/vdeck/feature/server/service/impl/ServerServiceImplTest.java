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

import com.github.pplociennik.vdeck.feature.backup.service.BackupPathService;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import com.github.pplociennik.vdeck.feature.server.exc.ServerAdoptException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerKillException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerRestartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStopException;
import com.github.pplociennik.vdeck.feature.server.service.LaunchCommandService;
import com.github.pplociennik.vdeck.feature.server.service.ServerLogService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import com.github.pplociennik.vdeck.runtime.api.StopRequest;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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

    @Mock
    private BackupPathService backupPaths;

    @TempDir
    private Path directory;

    private ServerServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates the service with strict mocks, no shared stubbing and a restart start on the calling thread. */
    @BeforeEach
    void setUp() {
        underTest = new ServerServiceImpl( serverRuntime, launchProfileService, launchCommandService,
                serverLogService, validator, pathService, backupPaths, Runnable::run );
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
        verifyNoInteractions( launchProfileService, launchCommandService, serverLogService, validator,
                pathService, backupPaths );
        verify( serverRuntime, never() ).start( any(), any() );
    }

    /** Verifies that a concurrent accepted start remains a rejection rather than a technical failure. */
    @Test
    void shouldRejectStart_whenRuntimeWinsRace() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( eq( command ), any() ) ).willThrow( new IllegalStateException( "Busy" ) );

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
        given( serverRuntime.start( eq( command ), any() ) ).willThrow( cause );

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
        givenWorldContext();
        given( serverLogService.createLogFileForNewStart() )
                .willThrow( new UncheckedIOException( new IOException( "Unwritable" ) ) );

        // WHEN
        var thrown = catchThrowable( underTest::start );

        // THEN
        assertRejected( thrown, ServerMessage.LOG_DIRECTORY_NOT_WRITABLE );
        verifyNoInteractions( launchCommandService );
        verify( serverRuntime, never() ).start( any(), any() );
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
        verify( serverRuntime, never() ).start( any(), any() );
    }

    /** Verifies exact mirror content and absence of temporary artifacts after a successful launch. */
    @Test
    void shouldWriteMirrorScript_whenStartSucceeds() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( eq( command ), any() ) ).willReturn( runningStatus() );

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
        given( serverRuntime.start( eq( command ), any() ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( serverRuntime ).start( eq( command ), any() );
    }

    /** Verifies that the runtime receives exactly the specification built by the command service. */
    @Test
    void shouldHandBuiltSpecToRuntime_whenStartIsValidated() {
        // GIVEN
        var command = givenPreparedCommand();
        given( serverRuntime.start( eq( command ), any() ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( validator ).validate( new LaunchConfiguration( profile(), "" ),
                directory.resolve( "valheim_server.exe" ) );
        verify( serverRuntime ).start( eq( command ), any() );
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
            "serverLogService", "validator", "pathService", "backupPaths", "restartExecutor" } )
    void shouldThrowException_whenDependencyIsNull( String dependency ) {
        // GIVEN
        Executor restartExecutor = Runnable::run;

        // WHEN
        var thrown = catchThrowable( () -> new ServerServiceImpl(
                dependency.equals( "serverRuntime" ) ? null : serverRuntime,
                dependency.equals( "launchProfileService" ) ? null : launchProfileService,
                dependency.equals( "launchCommandService" ) ? null : launchCommandService,
                dependency.equals( "serverLogService" ) ? null : serverLogService,
                dependency.equals( "validator" ) ? null : validator,
                dependency.equals( "pathService" ) ? null : pathService,
                dependency.equals( "backupPaths" ) ? null : backupPaths,
                dependency.equals( "restartExecutor" ) ? null : restartExecutor ) );

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
        givenWorldContext();
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
        given( serverRuntime.start( eq( spec ), any() ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        verify( launchProfileService ).getConfiguration();
        verify( launchCommandService ).build( same( captured ),
                eq( log ) );
        verify( serverRuntime ).start( same( spec ), any() );
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
        given( serverRuntime.start( eq( spec ), any() ) ).willReturn( runningStatus() );

        // WHEN
        underTest.start();

        // THEN
        assertThat( directory.resolve( ServerService.START_SCRIPT_NAME ) ).hasContent( content );
        verify( serverRuntime ).start( eq( spec ), any() );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================


    /** Stop admission grants no force permission and never reads mutable launch settings. */
    @Test
    void shouldAdmitGracefulStop_whenLaunchIdentifierIsValid() {
        // GIVEN
        var request = ArgumentCaptor.forClass( StopRequest.class );
        // WHEN
        underTest.stop( "selected-launch" );
        // THEN
        verify( serverRuntime ).stop( request.capture() );
        assertThat( request.getValue().isForceConfirmed() ).isFalse();
        assertThat( request.getValue().getLaunchId() ).isEqualTo( "selected-launch" );
        verifyNoInteractions( launchProfileService, backupPaths );
    }

    /** Kill without manual risk acceptance is rejected before contacting the runtime. */
    @Test
    void shouldRejectKill_whenConfirmationIsAbsent() {
        // GIVEN
        // No risk authorization.
        // WHEN
        var failure = catchThrowable( () -> underTest.kill( "selected-launch", false ) );
        // THEN
        assertThat( failure ).isInstanceOfSatisfying(
                ServerKillException.class,
                rejection -> assertThat( rejection.getMessageKey() ).isEqualTo( ServerMessage.CONFIRMATION_REQUIRED ) );
        verifyNoInteractions( serverRuntime );
    }

    /** Confirmed Kill is scoped to one selected launch rather than a persistent setting. */
    @Test
    void shouldAdmitConfirmedKill_whenRiskIsAccepted() {
        // GIVEN
        var request = ArgumentCaptor.forClass( StopRequest.class );
        // WHEN
        underTest.kill( "selected-launch", true );
        // THEN
        verify( serverRuntime ).stop( request.capture() );
        assertThat( request.getValue().isForceConfirmed() ).isTrue();
        assertThat( request.getValue().getLaunchId() ).isEqualTo( "selected-launch" );
    }

    /** Busy runtime admission is a process rejection carrying no technical cause to the browser. */
    @Test
    void shouldTranslateStopRejection_whenRuntimeIsBusy() {
        // GIVEN
        given( serverRuntime.stop( any() ) ).willThrow( new IllegalStateException( "Busy" ) );
        // WHEN
        var failure = catchThrowable( () -> underTest.stop( "selected-launch" ) );
        // THEN
        assertThat( failure ).isInstanceOfSatisfying(
                ServerStopException.class,
                rejection -> assertThat( rejection.getMessageKey() ).isEqualTo( ServerMessage.STOP_REJECTED ) );
        assertThat( failure ).hasNoCause();
    }

    /** Stale recovery selections are ordinary rejections with no technical cause. */
    @Test
    void shouldRejectAdoption_whenSavedLaunchIsStale() {
        // GIVEN
        doThrow( new IllegalStateException( "Stale launch" ) ).when( serverRuntime ).adopt( "selected" );
        // WHEN
        var failure = catchThrowable( () -> underTest.adopt( "selected" ) );
        // THEN
        assertThat( failure ).isInstanceOf( ServerAdoptException.class ).hasNoCause();
        assertThat( ( ( ServerAdoptException ) failure ).getMessageKey() ).isEqualTo( ServerMessage.ADOPT_REJECTED );
    }

    /** Infrastructure failures preserve their cause and use the adoption-specific message. */
    @Test
    void shouldReportAdoptionFailure_whenRuntimeCannotReadRecoveryData() {
        // GIVEN
        var cause = new UncheckedIOException( new IOException( "Unavailable" ) );
        doThrow( cause ).when( serverRuntime ).adopt( "selected" );
        // WHEN
        var failure = catchThrowable( () -> underTest.adopt( "selected" ) );
        // THEN
        assertThat( failure ).isInstanceOf( ServerAdoptException.class ).hasCause( cause );
        assertThat( ( ( ServerAdoptException ) failure ).getMessageKey() ).isEqualTo( ServerMessage.ADOPT_FAILED );
    }

    /** Adoption delegates only the selected token, with no implicit process signal. */
    @Test
    void shouldDelegateAdoption_whenAdminSelectsSavedLaunch() {
        // GIVEN
        // Selected launch token.
        // WHEN
        underTest.adopt( "selected-launch" );
        // THEN
        verify( serverRuntime ).adopt( "selected-launch" );
        verify( serverRuntime, never() ).stop( any() );
    }

    /** Missing identifiers are rejected at the service boundary for every control command. */
    @Test
    void shouldRejectNullIdentifiers_whenControlIsRequested() {
        // GIVEN
        // No selected token.
        // WHEN
        var stop = catchThrowable( () -> underTest.stop( null ) );
        var restart = catchThrowable( () -> underTest.restart( null ) );
        var kill = catchThrowable( () -> underTest.kill( null, true ) );
        var adopt = catchThrowable( () -> underTest.adopt( null ) );
        // THEN
        assertThat( stop ).isInstanceOf( NullPointerException.class );
        assertThat( restart ).isInstanceOf( NullPointerException.class );
        assertThat( kill ).isInstanceOf( NullPointerException.class );
        assertThat( adopt ).isInstanceOf( NullPointerException.class );
        verifyNoInteractions( serverRuntime );
    }

    /** Restart begins with a graceful stop without force and starts the server again once that stop completes. */
    @Test
    void shouldStartAgain_whenRestartStopCompletes() {
        // GIVEN
        var request = ArgumentCaptor.forClass( StopRequest.class );
        given( serverRuntime.stop( request.capture() ) ).willReturn( CompletableFuture.completedFuture( null ) );
        var command = givenPreparedCommand();
        given( serverRuntime.start( eq( command ), any() ) ).willReturn( runningStatus() );

        // WHEN
        underTest.restart( "selected-launch" );

        // THEN
        assertThat( request.getValue().isForceConfirmed() ).isFalse();
        assertThat( request.getValue().getLaunchId() ).isEqualTo( "selected-launch" );
        verify( serverRuntime ).start( eq( command ), any() );
    }

    /** Admission returns while the stop is still running, before any setting is read or any process is created. */
    @Test
    void shouldNotStartYet_whenRestartStopIsStillRunning() {
        // GIVEN
        given( serverRuntime.stop( any() ) ).willReturn( new CompletableFuture<>() );

        // WHEN
        underTest.restart( "selected-launch" );

        // THEN
        verify( serverRuntime, never() ).start( any(), any() );
        verifyNoInteractions( launchProfileService, validator, serverLogService, launchCommandService );
    }

    /** A failed stop retains the process and abandons the restart without reading settings or starting. */
    @Test
    void shouldNotStartAgain_whenRestartStopFails() {
        // GIVEN
        var stopFailure = new IllegalStateException( "Both CTRL+C attempts exhausted" );
        given( serverRuntime.stop( any() ) ).willReturn( CompletableFuture.failedFuture( stopFailure ) );

        // WHEN
        underTest.restart( "selected-launch" );

        // THEN
        verify( serverRuntime, never() ).start( any(), any() );
        verifyNoInteractions( launchProfileService, validator, serverLogService, launchCommandService );
    }

    /** A start refused after the stop ends the restart quietly; the refusal never escapes the restart thread. */
    @Test
    void shouldLeaveServerStopped_whenRestartStartIsRejected() {
        // GIVEN
        given( serverRuntime.stop( any() ) ).willReturn( CompletableFuture.completedFuture( null ) );
        given( serverRuntime.status() ).willReturn( runningStatus() );

        // WHEN
        var thrown = catchThrowable( () -> underTest.restart( "selected-launch" ) );

        // THEN
        assertThat( thrown ).isNull();
        verify( serverRuntime, never() ).start( any(), any() );
    }

    /** A process creation failure after the stop is logged on the restart thread instead of reaching the caller. */
    @Test
    void shouldLeaveServerStopped_whenRestartStartFails() {
        // GIVEN
        given( serverRuntime.stop( any() ) ).willReturn( CompletableFuture.completedFuture( null ) );
        var command = givenPreparedCommand();
        given( serverRuntime.start( eq( command ), any() ) )
                .willThrow( new UncheckedIOException( new IOException( "Cannot create process" ) ) );

        // WHEN
        var thrown = catchThrowable( () -> underTest.restart( "selected-launch" ) );

        // THEN
        assertThat( thrown ).isNull();
        verify( serverRuntime ).start( eq( command ), any() );
    }

    /** A busy or stale runtime refuses the restart as a rejection carrying no technical cause. */
    @Test
    void shouldTranslateRestartRejection_whenRuntimeIsBusy() {
        // GIVEN
        given( serverRuntime.stop( any() ) ).willThrow( new IllegalStateException( "Busy" ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.restart( "selected-launch" ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying(
                ServerRestartException.class,
                rejection -> assertThat( rejection.getMessageKey() ).isEqualTo( ServerMessage.RESTART_REJECTED ) );
        assertThat( failure ).hasNoCause();
        verify( serverRuntime, never() ).start( any(), any() );
    }

    /** A malformed identifier refused by the stop request is a rejection, not a technical failure. */
    @Test
    void shouldRejectRestart_whenLaunchIdentifierIsMalformed() {
        // GIVEN
        // An identifier the stop request refuses.

        // WHEN
        var failure = catchThrowable( () -> underTest.restart( "../launch" ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying(
                ServerRestartException.class,
                rejection -> assertThat( rejection.getMessageKey() ).isEqualTo( ServerMessage.RESTART_REJECTED ) );
        verifyNoInteractions( serverRuntime );
    }

    /** A technical admission failure keeps its cause and uses the restart-specific message. */
    @Test
    void shouldReportRestartFailure_whenRuntimeAdmissionFails() {
        // GIVEN
        var cause = new UncheckedIOException( new IOException( "Unavailable" ) );
        given( serverRuntime.stop( any() ) ).willThrow( cause );

        // WHEN
        var failure = catchThrowable( () -> underTest.restart( "selected-launch" ) );

        // THEN
        assertThat( failure ).isInstanceOfSatisfying(
                ServerRestartException.class,
                rejection -> assertThat( rejection.getMessageKey() ).isEqualTo( ServerMessage.RESTART_FAILED ) );
        assertThat( failure ).hasCause( cause );
    }

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
        givenWorldContext();
        return spec;
    }

    private void givenWorldContext() {
        given( pathService.getAbsolutePath( "valheim-data" ) ).willReturn( directory.resolve( "valheim-data" ) );
        given( backupPaths.resolveRoot( directory.resolve( "valheim-data" ) ) )
                .willReturn( directory.resolve( "backups" ) );
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
