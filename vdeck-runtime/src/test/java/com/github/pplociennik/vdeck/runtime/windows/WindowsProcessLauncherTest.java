package com.github.pplociennik.vdeck.runtime.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

/** Verifies native resource ownership and failure cleanup without creating operating-system processes. */
@ExtendWith( MockitoExtension.class )
class WindowsProcessLauncherTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final MemorySegment JOB = MemorySegment.ofAddress( 101 );
    private static final MemorySegment PROCESS = MemorySegment.ofAddress( 102 );
    private static final MemorySegment THREAD = MemorySegment.ofAddress( 103 );
    private static final long PID = 42L;
    private static final Clock CLOCK = Clock.fixed( Instant.parse( "2026-10-06T12:00:00Z" ), ZoneOffset.UTC );

    @Mock
    private Win32 nativeCalls;

    @Mock
    private LongFunction< Optional< ProcessHandle > > processLookup;

    @Mock
    private ProcessHandle handle;

    private WindowsProcessLauncher underTest;

    private LaunchSpec specification;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates fresh native boundaries and an absolute launch specification. */
    @BeforeEach
    void setUp() {
        underTest = new WindowsProcessLauncher( nativeCalls, processLookup, CLOCK );
        Path base = Path.of( "test-installation" ).toAbsolutePath();
        specification = new LaunchSpec( base.resolve( "server.exe" ), List.of( "-password", "synthetic-secret" ),
                Map.of( "SteamAppId", "892970" ), base, base.resolve( "run.log" ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies suspended creation, job assignment before resumption and retention of only the job handle. */
    @Test
    void shouldOwnProcessBeforeResuming_whenLaunchSucceeds() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.assignProcessToJobObject( JOB, PROCESS ) ).willReturn( 1 );
        given( nativeCalls.resumeThread( THREAD ) ).willReturn( 1 );
        given( processLookup.apply( PID ) ).willReturn( Optional.of( handle ) );

        // WHEN
        var result = underTest.launch( specification );

        // THEN
        assertThat( result.handle() ).isSameAs( handle );
        assertThat( result.startedAt() ).isEqualTo( CLOCK.instant() );
        assertThat( result.logFile() ).isEqualTo( specification.logFile() );
        var order = inOrder( nativeCalls );
        order.verify( nativeCalls ).createJobObject();
        order.verify( nativeCalls ).setInformationJobObject( eq( JOB ), anyInt(), any(), anyInt() );
        order.verify( nativeCalls ).createProcess( any(), any(),
                eq( Win32.CREATE_NO_WINDOW | Win32.CREATE_UNICODE_ENVIRONMENT | Win32.CREATE_SUSPENDED ),
                any(), any(), any(), any() );
        order.verify( nativeCalls ).assignProcessToJobObject( JOB, PROCESS );
        order.verify( nativeCalls ).resumeThread( THREAD );
        order.verify( nativeCalls ).closeHandle( THREAD );
        order.verify( nativeCalls ).closeHandle( PROCESS );
        verify( nativeCalls, never() ).closeHandle( JOB );
        verify( nativeCalls, never() ).terminateProcess( any() );
    }

    /** Verifies that no configuration or process call follows failure to acquire the job. */
    @Test
    void shouldStopBeforeCreatingProcess_whenJobCreationFails() {
        // GIVEN
        given( nativeCalls.createJobObject() ).willReturn( MemorySegment.NULL );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "CreateJobObjectW" );
        verify( nativeCalls, never() ).setInformationJobObject( any(), anyInt(), any(), anyInt() );
        verify( nativeCalls, never() ).closeHandle( any() );
        verifyNoInteractions( processLookup );
    }

    /** Verifies release of a job whose kill-on-close policy could not be configured. */
    @Test
    void shouldCloseJob_whenJobConfigurationFails() {
        // GIVEN
        given( nativeCalls.createJobObject() ).willReturn( JOB );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "SetInformationJobObject" );
        verify( nativeCalls ).closeHandle( JOB );
        verifyNoInteractions( processLookup );
    }

    /** Verifies that a native invocation exception also releases the job acquired before configuration. */
    @Test
    void shouldCloseJob_whenJobConfigurationThrows() {
        // GIVEN
        given( nativeCalls.createJobObject() ).willReturn( JOB );
        var failure = new IllegalStateException( "Synthetic native invocation failure" );
        given( nativeCalls.setInformationJobObject( eq( JOB ), anyInt(), any(), anyInt() ) ).willThrow( failure );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertThat( thrown ).isSameAs( failure );
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies that failed process creation releases the job without closing uninitialized process handles. */
    @Test
    void shouldCloseOnlyJob_whenProcessCreationFails() {
        // GIVEN
        givenConfiguredJob();
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "CreateProcessW" );
        verify( nativeCalls ).closeHandle( JOB );
        verify( nativeCalls, never() ).closeHandle( PROCESS );
        verify( nativeCalls, never() ).closeHandle( THREAD );
        verifyNoInteractions( processLookup );
    }

    /** Verifies direct termination of an unassigned process before releasing its native handles. */
    @Test
    void shouldTerminateUnassignedProcess_whenJobAssignmentFails() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.getLastError() ).willReturn( 5 );
        given( nativeCalls.terminateProcess( PROCESS ) ).willReturn( 1 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "AssignProcessToJobObject" );
        var order = inOrder( nativeCalls );
        order.verify( nativeCalls ).assignProcessToJobObject( JOB, PROCESS );
        order.verify( nativeCalls ).terminateProcess( PROCESS );
        order.verify( nativeCalls ).closeHandle( THREAD );
        order.verify( nativeCalls ).closeHandle( PROCESS );
        order.verify( nativeCalls ).closeHandle( JOB );
        verify( nativeCalls, never() ).resumeThread( any() );
    }

    /** Verifies that failed emergency cleanup remains visible without hiding the original assignment error. */
    @Test
    void shouldRetainCleanupFailure_whenUnassignedProcessCannotBeTerminated() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "AssignProcessToJobObject" );
        assertThat( thrown.getSuppressed() ).singleElement().satisfies( failure ->
                assertThat( failure ).isInstanceOf( UncheckedIOException.class )
                        .hasMessageContaining( "TerminateProcess" ) );
        verify( nativeCalls ).closeHandle( PROCESS );
        verify( nativeCalls ).closeHandle( THREAD );
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies a thrown native assignment error still terminates the unowned process. */
    @Test
    void shouldTerminateUnassignedProcess_whenNativeAssignmentThrows() {
        // GIVEN
        givenCreatedProcess();
        var failure = new IllegalStateException( "Synthetic assignment invocation failure" );
        given( nativeCalls.assignProcessToJobObject( JOB, PROCESS ) ).willThrow( failure );
        given( nativeCalls.terminateProcess( PROCESS ) ).willReturn( 1 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertThat( thrown ).isSameAs( failure );
        verify( nativeCalls ).terminateProcess( PROCESS );
        verify( nativeCalls ).closeHandle( THREAD );
        verify( nativeCalls ).closeHandle( PROCESS );
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies a thrown cleanup error is suppressed and all native handles are still released. */
    @Test
    void shouldRetainOriginalFailure_whenEmergencyTerminationThrows() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.getLastError() ).willReturn( 5 );
        var cleanupFailure = new IllegalStateException( "Synthetic cleanup invocation failure" );
        given( nativeCalls.terminateProcess( PROCESS ) ).willThrow( cleanupFailure );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "AssignProcessToJobObject" );
        assertThat( thrown.getSuppressed() ).containsExactly( cleanupFailure );
        verify( nativeCalls ).closeHandle( THREAD );
        verify( nativeCalls ).closeHandle( PROCESS );
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies that closing the owning job reaps the assigned process after failed resumption. */
    @Test
    void shouldReapAssignedProcess_whenThreadCannotBeResumed() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.assignProcessToJobObject( JOB, PROCESS ) ).willReturn( 1 );
        given( nativeCalls.resumeThread( THREAD ) ).willReturn( Win32.INVALID_HANDLE );
        given( nativeCalls.getLastError() ).willReturn( 5 );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );

        // THEN
        assertNativeFailure( thrown, "ResumeThread" );
        verify( nativeCalls ).closeHandle( THREAD );
        verify( nativeCalls ).closeHandle( PROCESS );
        verify( nativeCalls ).closeHandle( JOB );
        verify( nativeCalls, never() ).terminateProcess( any() );
        verifyNoInteractions( processLookup );
    }

    /** Verifies that a failed Java handle lookup does not retain a closed job for the next launch. */
    @Test
    void shouldForgetFailedJob_whenProcessDisappearsBeforeLookup() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.assignProcessToJobObject( JOB, PROCESS ) ).willReturn( 1 );
        given( processLookup.apply( PID ) ).willReturn( Optional.empty() ).willReturn( Optional.of( handle ) );

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( specification ) );
        var result = underTest.launch( specification );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "disappeared" ).hasMessageNotContaining( "synthetic-secret" );
        assertThat( result.handle() ).isSameAs( handle );
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies that the previous successful job is released exactly once before another launch. */
    @Test
    void shouldClosePreviousJob_whenAnotherProcessIsLaunched() {
        // GIVEN
        givenCreatedProcess();
        given( nativeCalls.assignProcessToJobObject( JOB, PROCESS ) ).willReturn( 1 );
        given( processLookup.apply( PID ) ).willReturn( Optional.of( handle ) );
        underTest.launch( specification );

        // WHEN
        underTest.launch( specification );

        // THEN
        verify( nativeCalls ).closeHandle( JOB );
    }

    /** Verifies null rejection before any native resource is acquired. */
    @Test
    void shouldRejectNullSpecification_whenLaunchIsRequested() {
        // GIVEN
        // No specification.

        // WHEN
        var thrown = catchThrowable( () -> underTest.launch( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchSpec" );
        verifyNoInteractions( nativeCalls, processLookup );
    }

    /**
     * Verifies each required boundary independently.
     *
     * @param dependency
     *         missing constructor dependency
     */
    @ParameterizedTest( name = "[{index}] null {0}" )
    @ValueSource( strings = { "nativeCalls", "processLookup", "clock" } )
    void shouldRejectNullDependency_whenConstructed( String dependency ) {
        // GIVEN
        // One missing dependency.

        // WHEN
        var thrown = catchThrowable( () -> new WindowsProcessLauncher(
                dependency.equals( "nativeCalls" ) ? null : nativeCalls,
                dependency.equals( "processLookup" ) ? null : processLookup,
                dependency.equals( "clock" ) ? null : CLOCK ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( dependency );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenConfiguredJob() {
        given( nativeCalls.createJobObject() ).willReturn( JOB );
        given( nativeCalls.setInformationJobObject( eq( JOB ),
                eq( Win32.JOB_OBJECT_EXTENDED_LIMIT_INFORMATION ), any(), anyInt() ) ).willAnswer( invocation -> {
                    MemorySegment limits = invocation.getArgument( 2 );
                    assertThat( limits.get( ValueLayout.JAVA_INT, Win32.LIMIT_FLAGS_OFFSET ) )
                            .isEqualTo( Win32.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE );
                    return 1;
                } );
    }

    private void givenCreatedProcess() {
        givenConfiguredJob();
        given( nativeCalls.createProcess( any(), any(), anyInt(), any(), any(), any(), any() ) )
                .willAnswer( invocation -> {
                    MemorySegment information = invocation.getArgument( 6 );
                    information.set( ValueLayout.ADDRESS, 0, PROCESS );
                    information.set( ValueLayout.ADDRESS, 8, THREAD );
                    information.set( ValueLayout.JAVA_INT, 16, ( int ) PID );
                    return 1;
                } );
    }

    private static void assertNativeFailure( Throwable thrown, String operation ) {
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( operation ).hasMessageContaining( "5" )
                .hasMessageNotContaining( "synthetic-secret" );
    }
}
