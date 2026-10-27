package com.github.pplociennik.vdeck.launcher.exc;

import jakarta.annotation.Nonnull;
import lombok.Getter;

import static java.util.Objects.requireNonNull;

/**
 * Rejection of a start setting in {@code vDeck-start.properties}. Carries only the key, never the value,
 * so the launcher can name the faulty setting without echoing user input.
 */
@Getter
public final class InvalidStartSettingException extends RuntimeException {

    /**
     * Key of the rejected setting.
     */
    private final String key;

    /**
     * Creates the rejection.
     *
     * @param key
     *         key of the rejected setting
     * @throws NullPointerException
     *         if the key is null
     */
    public InvalidStartSettingException( @Nonnull String key ) {
        super( "Invalid start setting " + requireNonNull( key, "key must not be null" ) );
        this.key = key;
    }
}
