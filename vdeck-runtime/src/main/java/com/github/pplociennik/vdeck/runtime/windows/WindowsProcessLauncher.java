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
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Objects.requireNonNull;

/**
 * Creates the Valheim server process on Windows through {@code CreateProcessW}, and binds its lifetime
 * to the panel's by placing it in a job object.
 *
 * <p>The order of the native calls is what makes the binding airtight:
 *
 * <pre>
 * CreateJobObject
 *   -&gt; SetInformationJobObject( KILL_ON_JOB_CLOSE )
 *   -&gt; CreateProcessW( CREATE_SUSPENDED )    the process exists but has not run yet
 *   -&gt; AssignProcessToJobObject
 *   -&gt; ResumeThread                          only now does the server begin to execute
 * </pre>
 *
 * <p>Creating the process suspended matters: without it there is a window in which the process is
 * already running but not yet in the job, and a panel that died inside that window would leave an
 * orphan. Nothing the server does can escape the job afterwards.
 *
 * <p><strong>The job handle is deliberately never closed.</strong> It is released by the operating
 * system when the panel process ends — however it ends — and that release is what terminates the
 * server. Closing it earlier would kill the server immediately.
 *
 * <p>This class is not thread-safe. It is called only from the runtime's single lifecycle thread.
 */
@Log4j2
public final class WindowsProcessLauncher implements GameProcessLauncher {

    /**
     * Handle of the job owning the current server, held open for as long as the panel lives. A
     * previous job is closed when a new server is started, which also reaps anything still left in it.
     */
    private MemorySegment jobHandle;

    @Override
    public @Nonnull RunningServer launch( @Nonnull LaunchSpec launchSpec ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        log.debug( "Creating server process from {}", launchSpec );

        closePreviousJob();
        MemorySegment job = createKillOnCloseJob();
        try ( Arena arena = Arena.ofConfined() ) {
            long pid = createProcessInJob( arena, launchSpec, job );
            jobHandle = job;
            Instant startedAt = Instant.now();
            ProcessHandle handle = ProcessHandle.of( pid ).orElseThrow(
                    () -> new UncheckedIOException( new IOException(
                            "Server process " + pid + " disappeared immediately after being created" ) ) );
            log.info( "Created server process {} bound to the panel's lifetime", pid );
            return new RunningServer( handle, startedAt, launchSpec.logFile() );
        } catch ( RuntimeException exception ) {
            // The job owns nothing useful now; closing it reaps a process that may have been created
            // before the failure, so a half-finished launch cannot leave an orphan behind.
            Win32.closeHandle( job );
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
        if ( Win32.createProcess( applicationName, commandLine, creationFlags, environment, workingDirectory,
                startupInfo, processInformation ) == 0 ) {
            throw failure( "CreateProcessW", Win32.getLastError() );
        }

        MemorySegment processHandle = processInformation.get( ValueLayout.ADDRESS, 0L );
        MemorySegment threadHandle = processInformation.get( ValueLayout.ADDRESS, 8L );
        long pid = Integer.toUnsignedLong( processInformation.get( ValueLayout.JAVA_INT, 16L ) );
        try {
            if ( Win32.assignProcessToJobObject( job, processHandle ) == 0 ) {
                throw failure( "AssignProcessToJobObject", Win32.getLastError() );
            }
            if ( Win32.resumeThread( threadHandle ) == Win32.INVALID_HANDLE ) {
                throw failure( "ResumeThread", Win32.getLastError() );
            }
            return pid;
        } finally {
            // The panel tracks the process through ProcessHandle, so these two handles are not needed
            // once the process is running; the job keeps the ownership relation alive.
            Win32.closeHandle( threadHandle );
            Win32.closeHandle( processHandle );
        }
    }

    private @Nonnull MemorySegment createKillOnCloseJob() {
        MemorySegment job = Win32.createJobObject();
        if ( job.equals( MemorySegment.NULL ) ) {
            throw failure( "CreateJobObjectW", Win32.getLastError() );
        }
        try ( Arena arena = Arena.ofConfined() ) {
            MemorySegment limits = arena.allocate( Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION );
            limits.set( ValueLayout.JAVA_INT, Win32.LIMIT_FLAGS_OFFSET, Win32.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE );
            int size = ( int ) Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION.byteSize();
            if ( Win32.setInformationJobObject( job, Win32.JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, limits,
                    size ) == 0 ) {
                int error = Win32.getLastError();
                Win32.closeHandle( job );
                throw failure( "SetInformationJobObject", error );
            }
        }
        return job;
    }

    private void closePreviousJob() {
        if ( jobHandle == null ) {
            return;
        }
        Win32.closeHandle( jobHandle );
        jobHandle = null;
        log.debug( "Released the job object of the previous server" );
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