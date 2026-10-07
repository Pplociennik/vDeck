package com.github.pplociennik.vdeck.runtime.windows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies the observable contracts of the server-stop implementation in isolation. */
class JvmConsoleControlSenderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private JvmConsoleControlSender underTest;

    private Process process;

    private AtomicReference< ProcessBuilder > invocation;

    private ProcessIdentity identity;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a fresh isolated fixture. */
    @BeforeEach
    void setUp() throws Exception {
        process = mock( Process.class );
        invocation = new AtomicReference<>();
        identity = new ProcessIdentity( 42, Instant.EPOCH, Path.of( "with spaces/server.exe" ).toAbsolutePath() );
        underTest = new JvmConsoleControlSender( List.of( "javaw.exe", "-jar", "runner with spaces.jar" ), builder -> {
            invocation.set( builder );
            return process;
        } );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================


    /** Construction remains idle until the runtime requests a signal. */
    @Test
    void shouldRemainIdle_whenSenderIsCreated() {
        // GIVEN
        // The process boundary was installed during setup.
        // WHEN
        var command = invocation.get();
        // THEN
        assertThat( command ).isNull();
        verifyNoInteractions( process );
    }

    /** Starts a fresh helper for each request rather than keeping an idle subprocess. */
    @Test
    void shouldStartNewHelper_whenAnotherSignalIsRequested() throws Exception {
        // GIVEN
        given( process.getOutputStream() ).willReturn( new ByteArrayOutputStream() );
        given( process.waitFor( 1000, TimeUnit.MILLISECONDS ) ).willReturn( true );
        underTest.send( identity, Duration.ofSeconds( 1 ) );
        ProcessBuilder firstInvocation = invocation.get();
        // WHEN
        underTest.send( identity, Duration.ofSeconds( 1 ) );
        // THEN
        assertThat( invocation.get() ).isNotSameAs( firstInvocation );
        assertThat( invocation.get().command() ).containsExactlyElementsOf( firstInvocation.command() );
        verify( process, times( 2 ) ).waitFor( 1000, TimeUnit.MILLISECONDS );
    }

    /** Uses separate arguments and discards output without a shell or inherited console. */
    @Test
    void shouldSendExactIdentity_whenHelperSucceeds() throws Exception {
        // GIVEN
        given( process.getOutputStream() ).willReturn( new ByteArrayOutputStream() );
        given( process.waitFor( 1000, TimeUnit.MILLISECONDS ) ).willReturn( true );
        // WHEN
        underTest.send( identity, Duration.ofSeconds( 1 ) );
        // THEN
        assertThat( invocation.get().command() ).containsExactly( "javaw.exe", "-jar", "runner with spaces.jar",
                "--ctrl-sender", "42", Instant.EPOCH.toString(), identity.executable().toString() );
        assertThat( invocation.get().redirectOutput() ).isEqualTo( ProcessBuilder.Redirect.DISCARD );
        assertThat( invocation.get().redirectError() ).isEqualTo( ProcessBuilder.Redirect.DISCARD );
        verify( process, never() ).destroyForcibly();
    }

    /** Kills only the timed-out helper and never treats timeout as signal success. */
    @Test
    void shouldRejectSignal_whenHelperTimesOut() throws Exception {
        // GIVEN
        given( process.getOutputStream() ).willReturn( new ByteArrayOutputStream() );
        given( process.isAlive() ).willReturn( true );
        // WHEN
        var failure = catchThrowable( () -> underTest.send( identity, Duration.ofSeconds( 1 ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "timed out" );
        verify( process ).destroyForcibly();
    }

    /** Propagates a helper rejection rather than continuing a force ladder. */
    @Test
    void shouldRejectSignal_whenHelperExitsNonzero() throws Exception {
        // GIVEN
        given( process.getOutputStream() ).willReturn( new ByteArrayOutputStream() );
        given( process.waitFor( 1000, TimeUnit.MILLISECONDS ) ).willReturn( true );
        given( process.exitValue() ).willReturn( 1 );
        // WHEN
        var failure = catchThrowable( () -> underTest.send( identity, Duration.ofSeconds( 1 ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "exit code 1" );
    }

    /** Rejects an exhausted budget before launching a process. */
    @Test
    void shouldRejectTimeout_whenBudgetIsNotPositive() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> underTest.send( identity, Duration.ZERO ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
        assertThat( invocation.get() ).isNull();
    }

    /** Rejects null boundaries before launching or signaling any process. */
    @Test
    void shouldRejectNull_whenSignalArgumentIsMissing() throws Exception {
        // GIVEN
        // No additional setup.
        // WHEN
        var identityFailure = catchThrowable( () -> underTest.send( null, Duration.ofSeconds( 1 ) ) );
        var timeoutFailure = catchThrowable( () -> underTest.send( identity, null ) );
        // THEN
        assertThat( identityFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "identity" );
        assertThat( timeoutFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "timeout" );
        assertThat( invocation.get() ).isNull();
    }
}
