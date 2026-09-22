package com.github.pplociennik.vdeck.shared.service;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;

/**
 * Defines access to the external properties files containing user-editable vDeck settings. A single
 * service handles every such file; each operation names the file it works on by its bare file name, the
 * same name {@link PathService#getConfigurationFile(String)} resolves inside the configuration directory.
 *
 * <p>A file has to be {@linkplain #loadFile(String, SystemPropertyValues) loaded} once, with the complete
 * set of settings it owns, before its values can be read or written. A setting is either mandatory or optional, as
 * {@link SystemPropertyIf#isMandatory()} declares: a mandatory setting is never blank, an optional one
 * may hold an empty value.
 */
public interface SystemPropertiesService {

    /**
     * Loads a properties file from the configuration directory, creating it and the directory with the
     * default values when they do not exist yet, and restoring every missing managed key. Reads and may
     * write the file system.
     *
     * @param fileName
     *         bare name of the file inside the configuration directory
     * @param defaultValues
     *         complete set of settings owned by the file, with their default values
     * @throws IllegalArgumentException
     *         if the name is blank, a setting has a blank name, or a mandatory setting has a blank default
     * @throws IllegalStateException
     *         if the file has already been loaded
     * @throws PathResolutionException
     *         if the name contains a directory part or the file would leave the installation directory
     * @throws UncheckedIOException
     *         if the file cannot be read or created
     */
    void loadFile( @Nonnull String fileName, @Nonnull SystemPropertyValues defaultValues );

    /**
     * Returns the current value of a setting owned by a loaded file.
     *
     * @param fileName
     *         bare name of the loaded file owning the setting
     * @param property
     *         setting to read
     * @return configured value; never {@code null}, and never blank for a mandatory setting
     * @throws IllegalStateException
     *         if the file has not been loaded
     * @throws IllegalArgumentException
     *         if the setting is not owned by the file
     */
    @Nonnull
    String getValue( @Nonnull String fileName, @Nonnull SystemPropertyIf property );

    /**
     * Changes a setting and persists the complete file atomically.
     *
     * @param fileName
     *         bare name of the loaded file owning the setting
     * @param property
     *         setting to change
     * @param value
     *         new value; surrounding whitespace is removed
     * @throws IllegalStateException
     *         if the file has not been loaded
     * @throws IllegalArgumentException
     *         if the setting is not owned by the file, or the value is blank for a mandatory setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    void setValue( @Nonnull String fileName, @Nonnull SystemPropertyIf property, @Nonnull String value );

    /**
     * Changes several settings of one file together and persists the complete file once, atomically.
     * Either every value is applied, or — when validation or writing fails — none is.
     *
     * @param fileName
     *         bare name of the loaded file owning the settings
     * @param values
     *         new values of the settings; surrounding whitespace is removed
     * @throws IllegalStateException
     *         if the file has not been loaded
     * @throws IllegalArgumentException
     *         if a setting is not owned by the file, or a value is blank for a mandatory setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    void setValues( @Nonnull String fileName, @Nonnull SystemPropertyValues values );
}
