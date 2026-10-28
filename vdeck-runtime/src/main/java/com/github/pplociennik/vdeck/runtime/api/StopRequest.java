package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/**
 * Immutable authorization for one launch. Only a manually confirmed Kill and a shutdown Kill chosen by the
 * panel close strategy (AD-031) permit force.
 */
@Getter
public final class StopRequest {

    private final String launchId;

    private final boolean forceConfirmed;

    private final boolean shutdown;

    private StopRequest( String launchId, boolean forceConfirmed, boolean shutdown ) {
        this.launchId = requireNonNull( launchId, "launchId must not be null" );
        if ( !launchId.matches( "[A-Za-z0-9-]{1,100}" ) ) {
            throw new IllegalArgumentException( "launchId must be a bounded safe identifier" );
        }
        this.forceConfirmed = forceConfirmed;
        this.shutdown = shutdown;
    }

    /**
     * Authorizes graceful stop only.
     *
     * @param launchId
     *         opaque identifier from the current runtime status
     * @return immutable request without force permission
     * @throws NullPointerException
     *         if the identifier is null
     * @throws IllegalArgumentException
     *         if the identifier is empty, unsafe or longer than 100 characters
     */
    public static @Nonnull StopRequest graceful( @Nonnull String launchId ) {
        return new StopRequest( launchId, false, false );
    }

    /**
     * Authorizes immediate force after a snapshot attempt, for this request only.
     * Snapshot failure does not revoke the permission. No graceful signals are sent by Kill.
     *
     * @param launchId
     *         launch whose data-loss risk the administrator explicitly accepted
     * @return immutable request carrying manual force permission
     * @throws NullPointerException
     *         if the identifier is null
     * @throws IllegalArgumentException
     *         if the identifier is empty, unsafe or longer than 100 characters
     */
    public static @Nonnull StopRequest confirmedKill( @Nonnull String launchId ) {
        return new StopRequest( launchId, true, false );
    }

    /**
     * Authorizes panel shutdown without force permission, including a still-starting process.
     *
     * @param launchId
     *         current managed launch
     * @return shutdown request without force permission
     * @throws NullPointerException
     *         if the identifier is null
     * @throws IllegalArgumentException
     *         if the identifier is empty, unsafe or longer than 100 characters
     */
    public static @Nonnull StopRequest shutdown( @Nonnull String launchId ) {
        return new StopRequest( launchId, false, true );
    }

    /**
     * Authorizes immediate force after a snapshot attempt while the panel shuts down, for this request only.
     * The panel close strategy {@code stop} sends it after graceful shutdown left a live process (AD-031);
     * unlike {@link #confirmedKill(String)} it is accepted after {@link ServerRuntime#shutdown()}.
     *
     * @param launchId
     *         current managed launch
     * @return shutdown request carrying force permission
     * @throws NullPointerException
     *         if the identifier is null
     * @throws IllegalArgumentException
     *         if the identifier is empty, unsafe or longer than 100 characters
     */
    public static @Nonnull StopRequest shutdownKill( @Nonnull String launchId ) {
        return new StopRequest( launchId, true, true );
    }
}
