package com.github.pplociennik.vdeck.runtime.windows;

import com.github.pplociennik.vdeck.runtime.api.ControlSenderCommand;
import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/** Starts an isolated helper on demand, without a shell, visible console, or idle thread. */
@Log4j2
public final class JvmConsoleControlSender implements ConsoleControlSender {

    private final List< String > prefix;

    private final Function< ProcessBuilder, Process > processStarter;

    /**
     * Creates an idle launcher using a command supplied by the application's composition root.
     *
     * @param prefix
     *         validated Java executable, native-access flag, and jar/classpath entry point
     * @throws NullPointerException
     *         if the prefix or one of its elements is null
     * @throws IllegalArgumentException
     *         if the prefix is empty
     */
    public JvmConsoleControlSender( @Nonnull List< String > prefix ) {
        this( prefix, JvmConsoleControlSender::startProcess );
    }

    /**
     * Creates the launcher with a process boundary for deterministic tests.
     *
     * @param prefix
     *         immutable helper invocation prefix
     * @param processStarter
     *         process boundary
     * @throws NullPointerException
     *         if a dependency or prefix element is null
     * @throws IllegalArgumentException
     *         if the prefix is empty
     */
    JvmConsoleControlSender( List< String > prefix, Function< ProcessBuilder, Process > processStarter ) {
        this.prefix = List.copyOf( requireNonNull( prefix, "prefix must not be null" ) );
        if ( prefix.isEmpty() ) {
            throw new IllegalArgumentException( "Helper invocation must not be empty" );
        }
        this.processStarter = requireNonNull( processStarter, "processStarter must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public void send( @Nonnull ProcessIdentity identity, @Nonnull Duration timeout ) {
        requireNonNull( identity, "identity must not be null" );
        requireNonNull( timeout, "timeout must not be null" );
        if ( timeout.isNegative() || timeout.isZero() ) {
            throw new IllegalArgumentException( "Helper timeout must be positive" );
        }
        List< String > command = new ArrayList<>( prefix );
        command.addAll( ControlSenderCommand.arguments( identity ) );
        ProcessBuilder builder = new ProcessBuilder( command )
                .redirectOutput( ProcessBuilder.Redirect.DISCARD )
                .redirectError( ProcessBuilder.Redirect.DISCARD );
        log.debug( "Launching isolated CTRL+C helper for process {}", identity.pid() );
        Process helper = processStarter.apply( builder );
        try {
            helper.getOutputStream().close();
            if ( !helper.waitFor( timeout.toMillis(), TimeUnit.MILLISECONDS ) ) {
                throw new IllegalStateException( "CTRL+C helper timed out" );
            }
            if ( helper.exitValue() != 0 ) {
                throw new IllegalStateException( "CTRL+C helper failed with exit code " + helper.exitValue() );
            }
            log.info( "CTRL+C helper completed for process {}", identity.pid() );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException( "CTRL+C helper interrupted", exception );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not close helper input", exception );
        } finally {
            if ( helper.isAlive() ) {
                helper.destroyForcibly();
            }
        }
    }

    private static Process startProcess( ProcessBuilder builder ) {
        try {
            return builder.start();
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not launch CTRL+C helper", exception );
        }
    }
}
