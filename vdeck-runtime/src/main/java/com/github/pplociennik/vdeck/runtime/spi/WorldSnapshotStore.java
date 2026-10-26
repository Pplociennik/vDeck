package com.github.pplociennik.vdeck.runtime.spi;

import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import jakarta.annotation.Nonnull;

import java.util.function.BooleanSupplier;

/** Publishes a complete BEST_EFFORT snapshot or reports failure; the lifecycle owner decides how to proceed. */
@FunctionalInterface
public interface WorldSnapshotStore {

    /**
     * Copies a stable world set, persists checksums and metadata, and publishes it atomically.
     *
     * @param world
     *         captured world and authorized destination
     * @param operationId
     *         unique launch and stop-operation correlation
     * @param allowed
     *         cancellation/deadline guard checked during copying and before publication
     * @throws NullPointerException
     *         if any argument is null
     * @throws RuntimeException
     *         if copying, structural validation, durability, or publication fails
     */
    void create( @Nonnull WorldContext world, @Nonnull String operationId, @Nonnull BooleanSupplier allowed );
}
