package com.github.pplociennik.vdeck.runtime.lifecycle;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Secret-free durable context; only the lifecycle owner decides whether its process may be adopted.
 *
 * @param id
 *         bounded opaque identifier of the captured launch
 * @param identity
 *         operating-system process identity verified before adoption and every signal
 * @param world
 *         original world and authorized snapshot directory
 * @param logFile
 *         absolute path of the captured launch's log
 */
record ManagedLaunch( String id, ProcessIdentity identity, WorldContext world, Path logFile ) {

    /**
     * Validates a complete saved launch.
     *
     * @param id
     *         bounded opaque launch identifier
     * @param identity
     *         complete process identity
     * @param world
     *         world selection captured at launch
     * @param logFile
     *         absolute launch log
     * @throws NullPointerException
     *         if a component is null
     * @throws IllegalArgumentException
     *         if the identifier or log path is invalid
     */
    ManagedLaunch {
        requireNonNull( id, "id must not be null" );
        requireNonNull( identity, "identity must not be null" );
        requireNonNull( world, "world must not be null" );
        requireNonNull( logFile, "logFile must not be null" );
        if ( !id.matches( "[a-zA-Z0-9-]{1,100}" ) || !logFile.isAbsolute() ) {
            throw new IllegalArgumentException( "Invalid managed launch" );
        }
    }
}
