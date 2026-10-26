package com.github.pplociennik.vdeck.feature.server.exc;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/** Ends explicit server adoption with either a rejection or a technical failure. */
@Getter
public final class ServerAdoptException extends RuntimeException {

    private final ServerMessage messageKey;

    /**
     * Creates a safe adoption process exception.
     *
     * @param messageKey
     *         closed translation key
     * @param cause
     *         technical failure, or null for rejection
     * @throws NullPointerException
     *         if the key is null
     */
    public ServerAdoptException( @Nonnull ServerMessage messageKey, @Nullable Throwable cause ) {
        super( "Server adoption not admitted", cause );
        this.messageKey = requireNonNull( messageKey, "messageKey must not be null" );
    }
}
