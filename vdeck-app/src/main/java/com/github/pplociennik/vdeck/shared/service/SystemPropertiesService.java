package com.github.pplociennik.vdeck.shared.service;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Defines access to one external properties file containing user-editable vDeck settings.
 *
 * <p>A setting is either required or optional, which the file's default values decide: a setting with a
 * non-blank default is required and never blank, a setting whose default is empty is optional and may
 * hold an empty value.
 */
public interface SystemPropertiesService {

    /**
     * Returns the current value of a setting owned by this file.
     *
     * @param property
     *         setting to read
     * @return configured value; never {@code null}, and never blank for a required setting
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
     *         if the setting is not owned by this file, or the value is blank for a required setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    void setValue( @Nonnull SystemPropertyIf property, @Nonnull String value );

    /**
     * Changes several settings together and persists the complete file once, atomically. Either every
     * value is applied, or — when validation or writing fails — none is.
     *
     * @param values
     *         new values keyed by setting; surrounding whitespace is removed
     * @throws IllegalArgumentException
     *         if a setting is not owned by this file, or a value is blank for a required setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    void setValues( @Nonnull Map< SystemPropertyIf, String > values );
}