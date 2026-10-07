package com.github.pplociennik.vdeck.runtime.windows;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.LongFunction;

import static java.util.Objects.requireNonNull;

/**
 * Sends CTRL+C from a short-lived helper JVM. Never invoke it inside the panel JVM: console state
 * belongs to the entire process, and detaching would affect the panel and its terminal.
 */
@Log4j2
public final class WindowsConsoleControlSender {

    private final Win32 nativeCalls;

    private final LongFunction< Optional< ProcessHandle > > processLookup;

    /** Creates the Windows helper boundary; construction is deferred until helper mode is selected. */
    public WindowsConsoleControlSender() {
        this( new Win32(), ProcessHandle::of );
    }

    /**
     * Creates the helper with controllable operating-system boundaries.
     *
     * @param nativeCalls
     *         console calls used only by this helper
     * @param processLookup
     *         process lookup used to verify the destination
     * @throws NullPointerException
     *         if a dependency is null
     */
    WindowsConsoleControlSender( @Nonnull Win32 nativeCalls,
                                 @Nonnull LongFunction< Optional< ProcessHandle > > processLookup ) {
        this.nativeCalls = requireNonNull( nativeCalls, "nativeCalls must not be null" );
        this.processLookup = requireNonNull( processLookup, "processLookup must not be null" );
    }

    /**
     * Verifies the process, attaches to its console and requests its normal CTRL+C handling.
     *
     * <p>Completion means the event was sent, not that the world was saved or the server exited.
     * Console detachment is attempted even after failure; cleanup failure preserves the original
     * error rather than replacing it. No server arguments or passwords are needed by this helper.</p>
     *
     * @param identity
     *         expected server identity, checked again immediately before sending
     * @throws NullPointerException
     *         if the identity is null
     * @throws IllegalStateException
     *         if the process is absent or its identity differs
     * @throws UncheckedIOException
     *         if path verification or a Windows console call fails
     */
    public void send( @Nonnull ProcessIdentity identity ) {
        requireNonNull( identity, "identity must not be null" );
        ProcessHandle process = processLookup.apply( identity.pid() ).orElseThrow(
                () -> new IllegalStateException( "Console destination process no longer exists" ) );
        identity.verify( process );
        requireSuccess( nativeCalls.freeConsole(), "FreeConsole" );
        requireSuccess( nativeCalls.attachConsole( ( int ) identity.pid() ), "AttachConsole" );
        RuntimeException failure = null;
        try {
            // AttachConsole resets control handlers, so the helper must ignore CTRL+C afterwards.
            requireSuccess( nativeCalls.ignoreConsoleControl(), "SetConsoleCtrlHandler" );
            identity.verify( process );
            requireSuccess( nativeCalls.sendConsoleControl(), "GenerateConsoleCtrlEvent" );
            log.info( "Sent CTRL+C to verified server process {}", identity.pid() );
        } catch ( RuntimeException exception ) {
            failure = exception;
            throw exception;
        } finally {
            detach( failure );
        }
    }

    private void requireSuccess( int result, String operation ) {
        if ( result == 0 ) {
            int error = nativeCalls.getLastError();
            throw new UncheckedIOException( new IOException(
                    "%s failed with Windows error %d".formatted( operation, error ) ) );
        }
    }

    private void detach( RuntimeException failure ) {
        try {
            requireSuccess( nativeCalls.freeConsole(), "FreeConsole cleanup" );
        } catch ( RuntimeException cleanupFailure ) {
            if ( failure == null ) {
                throw cleanupFailure;
            }
            failure.addSuppressed( cleanupFailure );
        }
    }
}
