package com.github.pplociennik.vdeck.feature.server.exc;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/** Ends Kill admission or confirmation validation without granting implicit force permission. */
@Getter
public final class ServerKillException extends RuntimeException {

    private final ServerMessage messageKey;

    /**
     * Creates a safe Kill process exception.
     *
     * @param messageKey
     *         closed translation key
     * @param cause
     *         technical failure, or null for rejection
     * @throws NullPointerException
     *         if the key is null
     */
    public ServerKillException( @Nonnull ServerMessage messageKey, @Nullable Throwable cause ) {
        super( "Server Kill not admitted", cause );
        this.messageKey = requireNonNull( messageKey, "messageKey must not be null" );
    }
}
