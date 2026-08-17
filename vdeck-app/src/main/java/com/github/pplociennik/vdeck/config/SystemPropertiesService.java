package com.github.pplociennik.vdeck.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Reads and writes one external properties file holding user-editable vDeck settings.
 * One instance serves exactly one file; the file and the set of settings it owns are fixed at
 * construction time, which is how support for further properties files is added later.
 *
 * <p>The file is read once, during construction, and kept in memory afterwards. vDeck runs as a
 * single user in at most one session, so no locking or reload is needed: a file edited by hand while
 * the panel is running is picked up on the next start.
 *
 * <p>Construction has side effects on disk. A missing file is created with default values, and a file
 * that lost a managed key has that key restored. This is also the migration path — settings added in
 * a new release appear in an existing installation on first start. Entries not owned by this service
 * are kept untouched, but comments and key order are lost once the application saves the file.
 *
 * <p>After construction every managed setting is guaranteed to have a non-blank value both in memory
 * and in the file, so {@link #getValue} is a plain lookup that never returns {@code null} or blank.
 *
 * <p>Values are never written to the log, only key names, because a settings file may hold secrets.
 */
@Log4j2
public class SystemPropertiesService {

    private static final String FILE_HEADER =
            "vDeck panel settings. Managed by vDeck; comments and key order are not preserved when "
                    + "the application saves this file.";

    private final Path propertiesFile;

    private final Properties defaults;

    private final Properties properties = new Properties();

    /**
     * Creates a service for one properties file, loading it or creating it with default values.
     * Reads and may write the file system.
     *
     * @param propertiesFile
     *         absolute path of the properties file this instance owns
     * @param defaultValues
     *         complete set of settings owned by the file, with their default values
     * @throws IllegalArgumentException
     *         if the path is not absolute, or a setting has a blank name or
     *         a blank default value
     * @throws UncheckedIOException
     *         if the file cannot be read or created
     */
    public SystemPropertiesService( @Nonnull Path propertiesFile,
                                    @Nonnull Map< SystemPropertyIf, String > defaultValues ) {
        this.propertiesFile = requireNonNull( propertiesFile, "propertiesFile must not be null" );
        requireNonNull( defaultValues, "defaultValues must not be null" );
        if ( !propertiesFile.isAbsolute() ) {
            throw new IllegalArgumentException( "propertiesFile must be absolute but was " + propertiesFile );
        }
        this.defaults = toDefaults( defaultValues );
        loadOrCreate();
    }

    /**
     * Returns the current value of a setting owned by this file.
     * Reads the in-memory state only.
     *
     * @param property
     *         setting to read
     * @return the configured value, or the default when the file did not provide one; never blank
     *
     * @throws IllegalArgumentException
     *         if the setting is not owned by this file
     */
    public @Nonnull String getValue( @Nonnull SystemPropertyIf property ) {
        return properties.getProperty( requireManaged( property ) );
    }

    /**
     * Changes a setting and persists the whole file immediately.
     * The file is replaced atomically, so a failure never leaves a partially written settings file.
     * When persisting fails the previous value is restored in memory before the exception is
     * propagated, so memory and disk cannot diverge.
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
    public void setValue( @Nonnull SystemPropertyIf property, @Nonnull String value ) {
        String name = requireManaged( property );
        requireNonNull( value, "value must not be null" );
        String strippedValue = value.strip();
        if ( strippedValue.isEmpty() ) {
            throw new IllegalArgumentException( "value must not be blank" );
        }

        String previousValue = properties.getProperty( name );
        properties.setProperty( name, strippedValue );
        try {
            save();
        } catch ( RuntimeException exception ) {
            properties.setProperty( name, previousValue );
            throw exception;
        }
        log.info( "Updated vDeck setting '{}' in file {}", name, propertiesFile );
    }

    private void loadOrCreate() {
        if ( !Files.exists( propertiesFile ) ) {
            properties.putAll( defaults );
            save();
            log.info( "Created vDeck settings file {} with {} default values", propertiesFile, properties.size() );
            return;
        }

        read();
        if ( restoreMissingValues() ) {
            save();
            log.info( "Loaded vDeck settings from {} and restored missing values", propertiesFile );
            return;
        }
        log.info( "Loaded vDeck settings from {}", propertiesFile );
    }

    private void read() {
        try ( BufferedReader reader = Files.newBufferedReader( propertiesFile, StandardCharsets.UTF_8 ) ) {
            properties.load( reader );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to read vDeck settings from " + propertiesFile, exception );
        }
        // Properties.load only trims leading whitespace, so trailing whitespace is removed here.
        for ( String name : properties.stringPropertyNames() ) {
            properties.setProperty( name, properties.getProperty( name ).strip() );
        }
    }

    private boolean restoreMissingValues() {
        boolean restored = false;
        for ( String name : defaults.stringPropertyNames() ) {
            String value = properties.getProperty( name );
            if ( value == null || value.isEmpty() ) {
                properties.setProperty( name, defaults.getProperty( name ) );
                log.warn( "Restored default value of vDeck setting '{}' missing in file {}", name, propertiesFile );
                restored = true;
            }
        }
        return restored;
    }

    private void save() {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile( propertiesFile.getParent(), "vDeck", ".properties.tmp" );
            try ( BufferedWriter writer = Files.newBufferedWriter( temporaryFile, StandardCharsets.UTF_8 ) ) {
                properties.store( writer, FILE_HEADER );
            }
            // Flush to the device before publishing, so a power loss cannot leave an empty settings file.
            try ( FileChannel channel = FileChannel.open( temporaryFile, StandardOpenOption.WRITE ) ) {
                channel.force( true );
            }
            Files.move( temporaryFile, propertiesFile, REPLACE_EXISTING, ATOMIC_MOVE );
            temporaryFile = null;
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to save vDeck settings to " + propertiesFile, exception );
        } finally {
            deleteIfPresent( temporaryFile );
        }
    }

    private void deleteIfPresent( @Nullable Path temporaryFile ) {
        if ( temporaryFile == null ) {
            return;
        }
        try {
            Files.deleteIfExists( temporaryFile );
        } catch ( IOException exception ) {
            log.warn( "Failed to delete temporary settings file {}", temporaryFile, exception );
        }
    }

    private @Nonnull String requireManaged( @Nonnull SystemPropertyIf property ) {
        requireNonNull( property, "property must not be null" );
        String name = property.getName();
        if ( !defaults.containsKey( name ) ) {
            throw new IllegalArgumentException(
                    "Property '%s' is not managed by file %s".formatted( name, propertiesFile ) );
        }
        return name;
    }

    private static @Nonnull Properties toDefaults( @Nonnull Map< SystemPropertyIf, String > defaultValues ) {
        Properties defaults = new Properties();
        defaultValues.forEach( ( property, defaultValue ) -> {
            requireNonNull( property, "defaultValues must not contain a null property" );
            requireNonNull( defaultValue, "defaultValues must not contain a null default value" );
            String name = property.getName();
            if ( name == null || name.isBlank() ) {
                throw new IllegalArgumentException( "defaultValues must not contain a property with a blank name" );
            }
            if ( defaultValue.isBlank() ) {
                throw new IllegalArgumentException(
                        "Default value of property '%s' must not be blank".formatted( name ) );
            }
            defaults.setProperty( name, defaultValue.strip() );
        } );
        return defaults;
    }
}