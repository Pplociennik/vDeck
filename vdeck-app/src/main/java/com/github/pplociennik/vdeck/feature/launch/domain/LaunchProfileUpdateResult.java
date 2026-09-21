package com.github.pplociennik.vdeck.feature.launch.domain;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of a launch profile update: whether it was stored and, when it was rejected, which message the
 * screen has to show. Carrying the message key here means the controller never translates an outcome
 * into wording of its own.
 *
 * @param messageKey
 *         translation key of the message explaining a rejection, {@code null} exactly when the profile
 *         was stored
 */
public record LaunchProfileUpdateResult( @Nullable TranslationKeyIf messageKey ) {

    /**
     * Creates the result of an update that stored the whole profile.
     *
     * @return successful result without a message key
     */
    public static @Nonnull LaunchProfileUpdateResult success() {
        return new LaunchProfileUpdateResult( null );
    }

    /**
     * Creates the result of a rejected update; nothing was stored.
     *
     * @param messageKey
     *         translation key of the message explaining the rejection to the user
     * @return rejected result carrying its own message key
     */
    public static @Nonnull LaunchProfileUpdateResult rejected( @Nonnull TranslationKeyIf messageKey ) {
        return new LaunchProfileUpdateResult( requireNonNull( messageKey, "messageKey must not be null" ) );
    }

    /**
     * Reports whether the profile was stored.
     *
     * @return {@code true} when the update passed every check and was written
     */
    public boolean isSuccessful() {
        return messageKey == null;
    }
}