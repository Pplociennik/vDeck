package com.github.pplociennik.vdeck.feature.launch.exc;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.dto.LaunchProfileForm;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Signals that an update of the launch profile was not stored, which ends the whole update. It is thrown
 * both when a check rejects the submitted settings and when the settings file cannot be written, and it
 * is answered by the launch feature's controller advice, which sends the user back to the panel with the
 * message and the submitted values.
 *
 * <p>The exception carries the message shown to the user as a {@link LaunchProfileMessage}, never as a
 * text, and the submitted form, so the panel can show the edits again. The form is always kept without
 * its secrets ({@link LaunchProfileForm#withoutSecrets()}), so the server password never travels with the
 * exception. A rejection has no cause; a failure keeps the failure that caused it.
 */
public class LaunchProfileUpdateException extends RuntimeException {

    private final LaunchProfileMessage messageKey;

    private final LaunchProfileForm submittedForm;

    private LaunchProfileUpdateException( @Nonnull LaunchProfileMessage messageKey,
                                          @Nonnull LaunchProfileForm submittedForm, @Nullable Throwable cause ) {
        super( "Launch profile update was not stored: " + messageKey.name(), cause );
        this.messageKey = messageKey;
        this.submittedForm = submittedForm.withoutSecrets();
    }

    /**
     * Creates the exception for settings rejected by a check; nothing was stored.
     *
     * @param messageKey
     *         message explaining the rejection to the user
     * @param form
     *         submitted settings; the exception keeps them without their secrets
     * @return exception without a cause
     */
    public static @Nonnull LaunchProfileUpdateException rejected( @Nonnull LaunchProfileMessage messageKey,
                                                                  @Nonnull LaunchProfileForm form ) {
        requireNonNull( messageKey, "messageKey must not be null" );
        requireNonNull( form, "form must not be null" );
        return new LaunchProfileUpdateException( messageKey, form, null );
    }

    /**
     * Creates the exception for settings that passed every check but could not be written.
     *
     * @param form
     *         submitted settings; the exception keeps them without their secrets
     * @param cause
     *         failure of the write
     * @return exception carrying {@link LaunchProfileMessage#SAVE_FAILED} and the cause
     */
    public static @Nonnull LaunchProfileUpdateException saveFailed( @Nonnull LaunchProfileForm form,
                                                                    @Nonnull Throwable cause ) {
        requireNonNull( form, "form must not be null" );
        requireNonNull( cause, "cause must not be null" );
        return new LaunchProfileUpdateException( LaunchProfileMessage.SAVE_FAILED, form, cause );
    }

    /**
     * Returns the message the panel has to show.
     *
     * @return translation key of the message explaining why nothing was stored
     */
    public @Nonnull LaunchProfileMessage getMessageKey() {
        return messageKey;
    }

    /**
     * Returns the submitted form to show again, never holding the server password.
     *
     * @return submitted form without its secrets
     */
    public @Nonnull LaunchProfileForm getSubmittedForm() {
        return submittedForm;
    }

    /**
     * Reports whether the update failed, rather than being rejected by a check.
     *
     * @return {@code true} when the exception has a cause, that is when the settings could not be written
     */
    public boolean isFailure() {
        return getCause() != null;
    }
}
