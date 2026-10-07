package com.github.pplociennik.vdeck.feature.server.exc;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/** Ends a rejected or failed stop admission; accepted asynchronous failures remain in runtime status. */
@Getter
public final class ServerStopException extends RuntimeException {

    private final ServerMessage messageKey;

    /**
     * Creates a safe stop process exception.
     *
     * @param messageKey
     *         closed translation key
     * @param cause
     *         technical failure, or null for rejection
     * @throws NullPointerException
     *         if the key is null
     */
    public ServerStopException( @Nonnull ServerMessage messageKey, @Nullable Throwable cause ) {
        super( "Server stop not admitted", cause );
        this.messageKey = requireNonNull( messageKey, "messageKey must not be null" );
    }
}
