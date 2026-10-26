package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/** Stops the game asynchronously before runtime destruction; failure never grants force permission. */
@Log4j2
@Component
class ServerShutdownLifecycle implements SmartLifecycle {

    private final ServerRuntime runtime;

    private volatile boolean running;

    /**
     * Creates the shutdown coordinator.
     *
     * @param runtime
     *         sole lifecycle owner
     * @throws NullPointerException
     *         if the runtime is null
     */
    ServerShutdownLifecycle( @Nonnull ServerRuntime runtime ) {
        this.runtime = requireNonNull( runtime, "runtime must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public void start() {
        running = true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** {@inheritDoc} */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1024;
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        stop( () -> { } );
    }

    /**
     * Awaits graceful shutdown or joins an already authorized stop operation before resource destruction.
     * Failed stop retains the process; this coordinator never grants force permission.
     *
     * @param callback
     *         Spring callback invoked once after completion, failure or rejected admission
     * @throws NullPointerException
     *         if the callback is null
     */
    @Override
    public void stop( @Nonnull Runnable callback ) {
        requireNonNull( callback, "callback must not be null" );
        running = false;
        AtomicBoolean completed = new AtomicBoolean();
        Runnable completeOnce = () -> {
            if ( completed.compareAndSet( false, true ) ) {
                callback.run();
            }
        };
        try {
            runtime.shutdown().whenComplete( ( ignored, failure ) -> {
                log.info( "Panel shutdown stop completed, successful {}", failure == null );
                completeOnce.run();
            } );
        } catch ( RuntimeException exception ) {
            log.warn( "Panel shutdown could not begin graceful stop; server is retained", exception );
            completeOnce.run();
        }
    }
}
