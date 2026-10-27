package com.github.pplociennik.vdeck.launcher.exc;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Failure of an update preparation, which never changes the installation. The launcher then starts the
 * installed version; it deletes the work files only when the worker has certainly finished.
 */
public final class UpdatePreparationException extends RuntimeException {

    private final boolean workerStopped;

    /**
     * Creates the failure.
     *
     * @param cause
     *         original failure
     * @param workerStopped
     *         whether the preparation worker has certainly finished
     * @throws NullPointerException
     *         if the cause is null
     */
    public UpdatePreparationException( @Nonnull Throwable cause, boolean workerStopped ) {
        super( "Update preparation failed", requireNonNull( cause, "cause must not be null" ) );
        this.workerStopped = workerStopped;
    }

    /**
     * Reports whether the worker has finished, so no late write can happen and work files may be deleted.
     *
     * @return {@code true} when the worker has stopped
     */
    public boolean isWorkerStopped() {
        return workerStopped;
    }
}
