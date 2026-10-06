package com.github.pplociennik.vdeck.runtime.windows;

import jakarta.annotation.Nonnull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * The few {@code kernel32} calls and structures vDeck needs to create the Valheim server process and
 * bind its lifetime to the panel's.
 *
 * <p>This is the only place in the project that touches native code. It deliberately exposes raw
 * handles and error codes rather than a friendly API: interpreting them, and above all closing them,
 * is {@link WindowsProcessLauncher}'s job, so ownership of a native resource stays with a single class
 * ({@code clean-code.md} §9).
 *
 * <p>Structure layouts are written out explicitly, including the padding the x64 ABI inserts, because
 * a wrong offset here would not fail loudly — it would silently pass a garbage value to the operating
 * system. Each layout's total size is asserted against the size Windows documents.
 */
final class Win32 {

    /**
     * Creates the process without allocating a console window. The server gets its own console, which
     * keeps a CTRL+C in the terminal that started vDeck from reaching it ({@code design.md} §6).
     */
    static final int CREATE_NO_WINDOW = 0x0800_0000;

    /**
     * States that the supplied environment block is UTF-16 rather than ANSI.
     */
    static final int CREATE_UNICODE_ENVIRONMENT = 0x0000_0400;

    /**
     * Creates the process suspended, so it can be put into the job object before it runs a single
     * instruction. Without this there is a window in which the process exists outside the job and
     * could survive the panel.
     */
    static final int CREATE_SUSPENDED = 0x0000_0004;

    /**
     * Terminates every process in the job when its last handle closes. This is what makes the server
     * die together with the panel, including when the panel is killed or crashes and no vDeck code
     * runs at all.
     */
    static final int JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x0000_2000;

    /**
     * {@code JobObjectExtendedLimitInformation} member of the {@code JOBOBJECTINFOCLASS} enumeration.
     */
    static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;

    /**
     * Value {@code CreateProcessW} and friends return in a handle field when they fail.
     */
    static final int INVALID_HANDLE = -1;

    /**
     * Layout of {@code STARTUPINFOW}; 104 bytes on x64.
     */
    static final MemoryLayout STARTUPINFOW = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName( "cb" ),
            MemoryLayout.paddingLayout( 4 ),
            ValueLayout.ADDRESS.withName( "lpReserved" ),
            ValueLayout.ADDRESS.withName( "lpDesktop" ),
            ValueLayout.ADDRESS.withName( "lpTitle" ),
            ValueLayout.JAVA_INT.withName( "dwX" ),
            ValueLayout.JAVA_INT.withName( "dwY" ),
            ValueLayout.JAVA_INT.withName( "dwXSize" ),
            ValueLayout.JAVA_INT.withName( "dwYSize" ),
            ValueLayout.JAVA_INT.withName( "dwXCountChars" ),
            ValueLayout.JAVA_INT.withName( "dwYCountChars" ),
            ValueLayout.JAVA_INT.withName( "dwFillAttribute" ),
            ValueLayout.JAVA_INT.withName( "dwFlags" ),
            ValueLayout.JAVA_SHORT.withName( "wShowWindow" ),
            ValueLayout.JAVA_SHORT.withName( "cbReserved2" ),
            MemoryLayout.paddingLayout( 4 ),
            ValueLayout.ADDRESS.withName( "lpReserved2" ),
            ValueLayout.ADDRESS.withName( "hStdInput" ),
            ValueLayout.ADDRESS.withName( "hStdOutput" ),
            ValueLayout.ADDRESS.withName( "hStdError" ) ).withName( "STARTUPINFOW" );

    /**
     * Layout of {@code PROCESS_INFORMATION}; 24 bytes on x64.
     */
    static final MemoryLayout PROCESS_INFORMATION = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName( "hProcess" ),
            ValueLayout.ADDRESS.withName( "hThread" ),
            ValueLayout.JAVA_INT.withName( "dwProcessId" ),
            ValueLayout.JAVA_INT.withName( "dwThreadId" ) ).withName( "PROCESS_INFORMATION" );

    /**
     * Layout of {@code JOBOBJECT_EXTENDED_LIMIT_INFORMATION}; 144 bytes on x64. Only
     * {@code BasicLimitInformation.LimitFlags} is ever written, the rest stays zeroed.
     */
    static final MemoryLayout JOBOBJECT_EXTENDED_LIMIT_INFORMATION = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName( "PerProcessUserTimeLimit" ),
            ValueLayout.JAVA_LONG.withName( "PerJobUserTimeLimit" ),
            ValueLayout.JAVA_INT.withName( "LimitFlags" ),
            MemoryLayout.paddingLayout( 4 ),
            ValueLayout.JAVA_LONG.withName( "MinimumWorkingSetSize" ),
            ValueLayout.JAVA_LONG.withName( "MaximumWorkingSetSize" ),
            ValueLayout.JAVA_INT.withName( "ActiveProcessLimit" ),
            MemoryLayout.paddingLayout( 4 ),
            ValueLayout.JAVA_LONG.withName( "Affinity" ),
            ValueLayout.JAVA_INT.withName( "PriorityClass" ),
            ValueLayout.JAVA_INT.withName( "SchedulingClass" ),
            MemoryLayout.sequenceLayout( 48, ValueLayout.JAVA_BYTE ).withName( "IoInfo" ),
            ValueLayout.JAVA_LONG.withName( "ProcessMemoryLimit" ),
            ValueLayout.JAVA_LONG.withName( "JobMemoryLimit" ),
            ValueLayout.JAVA_LONG.withName( "PeakProcessMemoryUsed" ),
            ValueLayout.JAVA_LONG.withName( "PeakJobMemoryUsed" ) )
            .withName( "JOBOBJECT_EXTENDED_LIMIT_INFORMATION" );

    /**
     * Byte offset of {@code BasicLimitInformation.LimitFlags} inside the extended limit structure.
     */
    static final long LIMIT_FLAGS_OFFSET = 16L;

    private static final Linker LINKER = Linker.nativeLinker();

    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup( "kernel32.dll", Arena.global() );

    private static final MethodHandle CREATE_PROCESS_W = downcall( "CreateProcessW",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS ) );

    private static final MethodHandle CREATE_JOB_OBJECT_W = downcall( "CreateJobObjectW",
            FunctionDescriptor.of( ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS ) );

    private static final MethodHandle SET_INFORMATION_JOB_OBJECT = downcall( "SetInformationJobObject",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT ) );

    private static final MethodHandle ASSIGN_PROCESS_TO_JOB_OBJECT = downcall( "AssignProcessToJobObject",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS ) );

    private static final MethodHandle RESUME_THREAD = downcall( "ResumeThread",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS ) );

    private static final MethodHandle TERMINATE_PROCESS = downcall( "TerminateProcess",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT ) );

    private static final MethodHandle CLOSE_HANDLE = downcall( "CloseHandle",
            FunctionDescriptor.of( ValueLayout.JAVA_INT, ValueLayout.ADDRESS ) );

    private static final MethodHandle GET_LAST_ERROR = downcall( "GetLastError",
            FunctionDescriptor.of( ValueLayout.JAVA_INT ) );

    /** Creates the native Windows boundary used by the process launcher. */
    Win32() {
    }

    /**
     * Calls {@code CreateProcessW}.
     *
     * @param applicationName
     *         UTF-16 executable path
     * @param commandLine
     *         writable UTF-16 command line, which the function is allowed to modify in place
     * @param creationFlags
     *         combination of the {@code CREATE_*} constants
     * @param environment
     *         UTF-16 environment block, terminated by two null characters
     * @param currentDirectory
     *         UTF-16 working directory of the new process
     * @param startupInfo
     *         populated {@code STARTUPINFOW}
     * @param processInformation
     *         structure the function fills with the new process's handles and identifiers
     * @return non-zero on success, zero on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int createProcess( @Nonnull MemorySegment applicationName, @Nonnull MemorySegment commandLine,
                              int creationFlags, @Nonnull MemorySegment environment,
                              @Nonnull MemorySegment currentDirectory, @Nonnull MemorySegment startupInfo,
                              @Nonnull MemorySegment processInformation ) {
        try {
            return ( int ) CREATE_PROCESS_W.invokeExact( applicationName, commandLine, MemorySegment.NULL,
                    MemorySegment.NULL, 0, creationFlags, environment, currentDirectory, startupInfo,
                    processInformation );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to CreateProcessW failed", throwable );
        }
    }

    /**
     * Creates an unnamed job object.
     *
     * @return handle of the new job object, or a null segment on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    @Nonnull MemorySegment createJobObject() {
        try {
            return ( MemorySegment ) CREATE_JOB_OBJECT_W.invokeExact( MemorySegment.NULL, MemorySegment.NULL );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to CreateJobObjectW failed", throwable );
        }
    }

    /**
     * Applies a limit structure to a job object.
     *
     * @param job
     *         job object to configure
     * @param informationClass
     *         one of the {@code JOBOBJECTINFOCLASS} values
     * @param information
     *         populated limit structure
     * @param informationLength
     *         size of the structure in bytes
     * @return non-zero on success, zero on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int setInformationJobObject( @Nonnull MemorySegment job, int informationClass,
                                        @Nonnull MemorySegment information, int informationLength ) {
        try {
            return ( int ) SET_INFORMATION_JOB_OBJECT.invokeExact( job, informationClass, information,
                    informationLength );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to SetInformationJobObject failed", throwable );
        }
    }

    /**
     * Puts a process into a job object.
     *
     * @param job
     *         job object that should own the process
     * @param process
     *         handle of the process to assign
     * @return non-zero on success, zero on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int assignProcessToJobObject( @Nonnull MemorySegment job, @Nonnull MemorySegment process ) {
        try {
            return ( int ) ASSIGN_PROCESS_TO_JOB_OBJECT.invokeExact( job, process );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to AssignProcessToJobObject failed", throwable );
        }
    }

    /**
     * Resumes a thread created in the suspended state.
     *
     * @param thread
     *         handle of the thread to resume
     * @return previous suspend count, or {@code -1} on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int resumeThread( @Nonnull MemorySegment thread ) {
        try {
            return ( int ) RESUME_THREAD.invokeExact( thread );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to ResumeThread failed", throwable );
        }
    }

    /**
     * Closes a native handle. Closing a job object handle is what triggers the kill-on-close limit.
     *
     * @param handle
     *         handle to close
     * @return non-zero on success, zero on failure
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int closeHandle( @Nonnull MemorySegment handle ) {
        try {
            return ( int ) CLOSE_HANDLE.invokeExact( handle );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to CloseHandle failed", throwable );
        }
    }

    /**
     * Returns the calling thread's last error code. Only meaningful immediately after a failed call.
     *
     * @return Windows system error code
     * @throws IllegalStateException
     *         if the native call itself cannot be performed
     */
    int getLastError() {
        try {
            return ( int ) GET_LAST_ERROR.invokeExact();
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to GetLastError failed", throwable );
        }
    }

    /**
     * Terminates a suspended process that could not be assigned to its owning job.
     *
     * @param process
     *         native handle of the unassigned process
     * @return non-zero on success, zero on failure
     * @throws IllegalStateException
     *         if the native invocation itself fails
     */
    int terminateProcess( @Nonnull MemorySegment process ) {
        try {
            return ( int ) TERMINATE_PROCESS.invokeExact( process, 1 );
        } catch ( Throwable throwable ) {
            throw new IllegalStateException( "Native call to TerminateProcess failed", throwable );
        }
    }

    private static @Nonnull MethodHandle downcall( @Nonnull String name, @Nonnull FunctionDescriptor descriptor ) {
        MemorySegment symbol = KERNEL32.find( name )
                .orElseThrow( () -> new IllegalStateException( "kernel32.dll does not export " + name ) );
        return LINKER.downcallHandle( symbol, descriptor );
    }
}
