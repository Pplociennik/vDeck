package com.github.pplociennik.vdeck.updater;

import jakarta.annotation.Nonnull;

/**
 * Observation point reached after every durable journal write and every file-system action of a
 * transaction. Production code ignores it; tests throw from it to interrupt a transaction at an exact
 * point and then verify recovery from the files left behind.
 */
@FunctionalInterface
interface Checkpoint {

    /**
     * Checkpoint used outside tests.
     */
    Checkpoint NONE = name -> {
    };

    /**
     * Reports that a named point of the transaction has been reached.
     *
     * @param name
     *         stable description of the point, such as {@code LIB:backup-moved}
     */
    void reached( @Nonnull String name );
}
