package com.github.pplociennik.vdeck.shared.exc;

import jakarta.annotation.Nonnull;

/**
 * Signals that a configured path was rejected before any file system operation was performed on it.
 * A path is rejected when it is absolute, syntactically invalid, or when it leaves the base directory
 * either directly or through a symbolic link or a reparse point.
 * This is a security-relevant condition, so it has a dedicated type that callers can catch and
 * distinguish from ordinary I/O failures.
 */
public class PathResolutionException extends RuntimeException {

    /**
     * Creates a new exception describing a rejected path.
     *
     * @param message
     *         description of the rejected path and of the rule that rejected it
     */
    public PathResolutionException( @Nonnull String message ) {
        super( message );
    }

    /**
     * Creates a new exception describing a rejected path together with its underlying cause.
     *
     * @param message
     *         description of the rejected path and of the rule that rejected it
     * @param cause
     *         the original failure that made the path unusable
     */
    public PathResolutionException( @Nonnull String message, @Nonnull Throwable cause ) {
        super( message, cause );
    }
}