package com.github.pplociennik.vdeck.runtime.windows;

import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.runtime.api.RunningServer;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.LongFunction;

import static java.util.Objects.requireNonNull;

/**
 * Creates an isolated Windows server console through CreateProcessW. A kill-on-close job protects
 * incomplete process creation: assignment precedes resumption and every failed handoff closes the job.
 * After capturing the operating-system start time, the launcher disables kill-on-close before returning
 * ownership to the runtime. Closing the panel therefore preserves a successfully created live server.
 * This launcher is confined to the runtime's lifecycle thread.
 */
@Log4j2
public final class WindowsProcessLauncher implements GameProcessLauncher {

    /**
     * Handle of the most recent job after its kill-on-close policy has been disabled.
     * The next launch closes this handle without terminating any successfully created server.
     */
    private MemorySegment jobHandle;

    private final Win32 nativeCalls;

    private final LongFunction< Optional< ProcessHandle > > processLookup;

    private final Clock clock;

    /** Creates the launcher using the Windows native API and the system clock. */
    public WindowsProcessLauncher() {
        this( new Win32(), ProcessHandle::of, Clock.systemUTC() );
    }

    /**
     * Creates a launcher with explicit operating-system boundaries.
     *
     * @param nativeCalls
     *         Windows calls whose handles are owned by this launcher
     * @param processLookup
     *         lookup of the Java handle for a created process
     * @param clock
     *         source of diagnostic request timestamps
     * @throws NullPointerException
     *         if any dependency is null
     */
    WindowsProcessLauncher( @Nonnull Win32 nativeCalls,
                            @Nonnull LongFunction< Optional< ProcessHandle > > processLookup,
                            @Nonnull Clock clock ) {
        this.nativeCalls = requireNonNull( nativeCalls, "nativeCalls must not be null" );
        this.processLookup = requireNonNull( processLookup, "processLookup must not be null" );
        this.clock = requireNonNull( clock, "clock must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull RunningServer launch( @Nonnull LaunchSpec launchSpec ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        log.debug( "Creating server process from {}", launchSpec );

        closePreviousJob();
        MemorySegment job = createKillOnCloseJob();
        try ( Arena arena = Arena.ofConfined() ) {
            long pid = createProcessInJob( arena, launchSpec, job );
            Instant requestedAt = clock.instant();
            ProcessHandle handle = processLookup.apply( pid ).orElseThrow(
                    () -> new UncheckedIOException( new IOException(
                            "Server process " + pid + " disappeared immediately after being created" ) ) );
            Instant startedAt = handle.info().startInstant().orElseThrow(
                    () -> new IllegalStateException( "Created process has no operating-system start time" ) );
            releaseKillOnClose( job );
            jobHandle = job;
            log.info( "Created server process {}; job no longer terminates it when the panel exits", pid );
            log.debug( "Process identity captured at {}", requestedAt );
            return new RunningServer( handle, startedAt, launchSpec.logFile() );
        } catch ( RuntimeException exception ) {
            // The job owns nothing useful now; closing it reaps a process that may have been created
            // before the failure, so a half-finished launch cannot leave an orphan behind.
            nativeCalls.closeHandle( job );
            throw exception;
        }
    }

    /**
     * Creates the process in the suspended state, puts it in the job and only then lets it run.
     *
     * @return identifier of the created process
     */
    private long createProcessInJob( @Nonnull Arena arena, @Nonnull LaunchSpec launchSpec,
                                     @Nonnull MemorySegment job ) {
        MemorySegment applicationName = utf16( arena, launchSpec.executable().toString() );
        MemorySegment commandLine = utf16( arena,
                WindowsCommandLine.build( launchSpec.executable(), launchSpec.arguments() ) );
        MemorySegment workingDirectory = utf16( arena, launchSpec.workingDirectory().toString() );
        MemorySegment environment = environmentBlock( arena, launchSpec.environment() );

        MemorySegment startupInfo = arena.allocate( Win32.STARTUPINFOW );
        startupInfo.set( ValueLayout.JAVA_INT, 0L, ( int ) Win32.STARTUPINFOW.byteSize() );
        MemorySegment processInformation = arena.allocate( Win32.PROCESS_INFORMATION );

        int creationFlags = Win32.CREATE_NO_WINDOW | Win32.CREATE_UNICODE_ENVIRONMENT | Win32.CREATE_SUSPENDED;
        if ( nativeCalls.createProcess( applicationName, commandLine, creationFlags, environment, workingDirectory,
                startupInfo, processInformation ) == 0 ) {
            throw failure( "CreateProcessW", nativeCalls.getLastError() );
        }

        MemorySegment processHandle = processInformation.get( ValueLayout.ADDRESS, 0L );
        MemorySegment threadHandle = processInformation.get( ValueLayout.ADDRESS, 8L );
        long pid = Integer.toUnsignedLong( processInformation.get( ValueLayout.JAVA_INT, 16L ) );
        try {
            assignToJob( job, processHandle );
            if ( nativeCalls.resumeThread( threadHandle ) == Win32.INVALID_HANDLE ) {
                throw failure( "ResumeThread", nativeCalls.getLastError() );
            }
            return pid;
        } finally {
            // The panel tracks the process through ProcessHandle, so these two handles are not needed
            // once the process is running; the job keeps the ownership relation alive.
            nativeCalls.closeHandle( threadHandle );
            nativeCalls.closeHandle( processHandle );
        }
    }

    /** A job cannot reap a process until assignment succeeds; failed assignment needs direct cleanup. */
    private void assignToJob( MemorySegment job, MemorySegment process ) {
        try {
            if ( nativeCalls.assignProcessToJobObject( job, process ) == 0 ) {
                throw failure( "AssignProcessToJobObject", nativeCalls.getLastError() );
            }
        } catch ( RuntimeException exception ) {
            try {
                if ( nativeCalls.terminateProcess( process ) == 0 ) {
                    exception.addSuppressed( failure( "TerminateProcess", nativeCalls.getLastError() ) );
                }
            } catch ( RuntimeException cleanupFailure ) {
                exception.addSuppressed( cleanupFailure );
            }
            throw exception;
        }
    }

    private @Nonnull MemorySegment createKillOnCloseJob() {
        MemorySegment job = nativeCalls.createJobObject();
        if ( job.equals( MemorySegment.NULL ) ) {
            throw failure( "CreateJobObjectW", nativeCalls.getLastError() );
        }
        try ( Arena arena = Arena.ofConfined() ) {
            MemorySegment limits = arena.allocate( Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION );
            limits.set( ValueLayout.JAVA_INT, Win32.LIMIT_FLAGS_OFFSET, Win32.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE );
            int size = ( int ) Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION.byteSize();
            if ( nativeCalls.setInformationJobObject( job, Win32.JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, limits,
                    size ) == 0 ) {
                int error = nativeCalls.getLastError();
                throw failure( "SetInformationJobObject", error );
            }
        } catch ( RuntimeException exception ) {
            nativeCalls.closeHandle( job );
            throw exception;
        }
        return job;
    }

    private void closePreviousJob() {
        if ( jobHandle == null ) {
            return;
        }
        nativeCalls.closeHandle( jobHandle );
        jobHandle = null;
        log.debug( "Released the job object of the previous server" );
    }

    /** A successfully handed-off server may survive failed stop and panel exit (AD-017). */
    private void releaseKillOnClose( MemorySegment job ) {
        try ( Arena arena = Arena.ofConfined() ) {
            MemorySegment limits = arena.allocate( Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION );
            limits.set( ValueLayout.JAVA_INT, Win32.LIMIT_FLAGS_OFFSET, 0 );
            if ( nativeCalls.setInformationJobObject( job, Win32.JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, limits,
                    ( int ) limits.byteSize() ) == 0 ) {
                throw failure( "SetInformationJobObject release kill-on-close", nativeCalls.getLastError() );
            }
        }
    }

    /**
     * Builds the UTF-16 environment block {@code CreateProcessW} expects: null-terminated
     * {@code NAME=VALUE} entries followed by one more null character. Windows requires the entries to
     * be sorted, case-insensitively, so they are put through a sorted map first.
     */
    private static @Nonnull MemorySegment environmentBlock( @Nonnull Arena arena,
                                                            @Nonnull Map< String, String > environment ) {
        Map< String, String > sorted = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        sorted.putAll( environment );

        StringBuilder block = new StringBuilder();
        sorted.forEach( ( name, value ) -> block.append( name ).append( '=' ).append( value ).append( '\0' ) );
        block.append( '\0' );

        byte[] bytes = block.toString().getBytes( StandardCharsets.UTF_16LE );
        return arena.allocateFrom( ValueLayout.JAVA_BYTE, bytes );
    }

    private static @Nonnull MemorySegment utf16( @Nonnull Arena arena, @Nonnull String value ) {
        return arena.allocateFrom( value, StandardCharsets.UTF_16LE );
    }

    /**
     * Turns a Windows error code into the failure the launcher contract declares. The command line is
     * never included, because it carries the server password ({@code security.md} §7).
     */
    private static @Nonnull UncheckedIOException failure( @Nonnull String call, int errorCode ) {
        return new UncheckedIOException(
                new IOException( "%s failed with Windows error %d".formatted( call, errorCode ) ) );
    }
}
