package com.github.pplociennik.vdeck.shared.service;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;

/**
 * Defines access to one external properties file containing user-editable vDeck settings.
 */
public interface SystemPropertiesService {

    /**
     * Returns the current value of a setting owned by this file.
     *
     * @param property
     *         setting to read
     * @return configured non-blank value
     * @throws IllegalArgumentException
     *         if the setting is not owned by this file
     */
    @Nonnull
    String getValue( @Nonnull SystemPropertyIf property );

    /**
     * Changes a setting and persists the complete file atomically.
     *
     * @param property
     *         setting to change
     * @param value
     *         new value; surrounding whitespace is removed
     * @throws IllegalArgumentException
     *         if the setting is not owned by this file, or the value is blank
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    void setValue( @Nonnull SystemPropertyIf property, @Nonnull String value );
}
