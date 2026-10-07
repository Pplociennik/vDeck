package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;

/** Distinguishes missing source saves from snapshot destination and other I/O failures. */
public final class WorldSaveMissingException extends UncheckedIOException {

    /**
     * Retains the missing source file or directory diagnostic without exposing paths in status.
     *
     * @param cause
     *         original source lookup failure
     * @throws NullPointerException
     *         if the cause is null
     */
    public WorldSaveMissingException( @Nonnull IOException cause ) {
        super( "Required world save is missing", requireNonNull( cause, "cause must not be null" ) );
    }
}
