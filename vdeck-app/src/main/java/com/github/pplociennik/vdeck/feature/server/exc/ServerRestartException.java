package com.github.pplociennik.vdeck.feature.server.exc;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/**
 * Ends a rejected or failed admission of the stop that begins a restart. The later, asynchronous stop and
 * start are reported by the runtime status and the panel log, never by this exception.
 */
@Getter
public final class ServerRestartException extends RuntimeException {

    private final ServerMessage messageKey;

    /**
     * Creates a safe restart process exception.
     *
     * @param messageKey
     *         closed translation key
     * @param cause
     *         technical failure, or null for rejection
     * @throws NullPointerException
     *         if the key is null
     */
    public ServerRestartException( @Nonnull ServerMessage messageKey, @Nullable Throwable cause ) {
        super( "Server restart not admitted", cause );
        this.messageKey = requireNonNull( messageKey, "messageKey must not be null" );
    }
}
