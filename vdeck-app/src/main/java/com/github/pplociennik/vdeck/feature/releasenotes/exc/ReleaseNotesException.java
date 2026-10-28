package com.github.pplociennik.vdeck.feature.releasenotes.exc;

import com.github.pplociennik.vdeck.feature.releasenotes.ReleaseNotesMessage;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Ends the acknowledgement of a change list that could not be stored (AD-014). The process has no rule
 * that could refuse it, so the exception always describes a failure and keeps its cause. The feature's
 * {@code @ControllerAdvice} is the only place that handles it.
 */
public final class ReleaseNotesException extends RuntimeException {

    private final ReleaseNotesMessage messageKey;

    private ReleaseNotesException( @Nonnull ReleaseNotesMessage messageKey, @Nonnull Throwable cause ) {
        super( "Release notes were not acknowledged: " + messageKey.name(), cause );
        this.messageKey = messageKey;
    }

    /**
     * Creates the exception ending an acknowledgement whose record could not be stored.
     *
     * @param cause
     *         persistence failure
     * @return exception describing the failure, keeping its cause
     * @throws NullPointerException
     *         if {@code cause} is {@code null}
     */
    public static @Nonnull ReleaseNotesException acknowledgementFailed( @Nonnull Throwable cause ) {
        return new ReleaseNotesException( ReleaseNotesMessage.ACKNOWLEDGEMENT_FAILED,
                requireNonNull( cause, "cause must not be null" ) );
    }

    /**
     * Returns the message to show the administrator.
     *
     * @return translation key of the reason the change list stays open
     */
    public @Nonnull ReleaseNotesMessage getMessageKey() {
        return messageKey;
    }
}
