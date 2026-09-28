package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Reads and writes one external properties file holding user-editable vDeck settings.
 * One instance serves exactly one file; the file and the set of settings it owns are fixed at
 * construction time. {@link SystemPropertiesServiceImpl} keeps one instance per loaded file.
 *
 * <p>The file is read once, during construction, and kept in memory afterwards. vDeck runs as a
 * single user in at most one session, but requests may overlap. Writers are serialized and publish
 * complete contents only after persistence succeeds. A file edited by hand while
 * the panel is running is picked up on the next start.
 *
 * <p>Construction has side effects on disk. A missing file is created with default values, together
 * with its missing parent directories, and a file that lost a managed key has that key restored. This
 * is also the migration path — settings added in a new release appear in an existing installation on
 * first start. Entries not owned by this service are kept untouched, but comments and key order are
 * lost once the application saves the file.
 *
 * <p>A {@linkplain SystemPropertyIf#isMandatory() mandatory} setting has a non-blank value both in
 * memory and in the file after construction: a blank stored value is replaced by the default. An
 * optional setting may legitimately be empty, so only its missing key is restored. {@link #getValue} is
 * therefore a plain lookup that never returns {@code null}.
 *
 * <p>Values are never written to the log, only key names, because a settings file may hold secrets.
 */
@Log4j2
final class PropertiesFile {

    private static final String FILE_HEADER =
            "vDeck panel settings. Managed by vDeck; comments and key order are not preserved when "
                    + "the application saves this file.";

    private final Path propertiesFile;

    private final Map< SystemPropertyIf, String > defaults;

    /** Published contents are never mutated after startup; writers replace the whole reference. */
    private volatile Properties properties = new Properties();

    /**
     * Creates the handler of one properties file, loading it or creating it with default values.
     * Reads and may write the file system.
     *
     * @param propertiesFile
     *         absolute path of the properties file this instance owns; missing parent directories
     *         are created together with the file
     * @param defaultValues
     *         complete set of settings owned by the file, with their default values
     * @throws IllegalArgumentException
     *         if the path is not absolute, or a setting has a blank name, or a mandatory setting has
     *         a blank default value
     * @throws UncheckedIOException
     *         if the file cannot be read or created
     */
    PropertiesFile( @Nonnull Path propertiesFile, @Nonnull SystemPropertyValues defaultValues ) {
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
     * @return the configured value, or the default when the file did not provide one; never
     *         {@code null}, and never blank for a mandatory setting
     *
     * @throws IllegalArgumentException
     *         if the setting is not owned by this file
     */
    @Nonnull
    String getValue( @Nonnull SystemPropertyIf property ) {
        return properties.getProperty( requireManaged( property ) );
    }

    /**
     * Changes a setting and persists the whole file immediately.
     * The file is replaced atomically, so a failure never leaves a partially written settings file.
     * The updated contents are published in memory only after persistence succeeds; a failure leaves
     * the previous contents available to readers.
     *
     * @param property
     *         setting to change
     * @param value
     *         new value; surrounding whitespace is removed
     * @throws IllegalArgumentException
     *         if the setting is not owned by this file, or the value is blank for a mandatory setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    synchronized void setValue( @Nonnull SystemPropertyIf property, @Nonnull String value ) {
        String name = requireManaged( property );
        requireNonNull( value, "value must not be null" );

        applyAndSave( Map.of( name, normalize( property, value ) ) );
        log.info( "Updated vDeck setting '{}' in file {}", name, propertiesFile );
    }

    /**
     * Changes several settings and persists the whole file once.
     * Every entry is validated before anything changes. Writers are serialized, and a complete new
     * in-memory version is published only after the atomic file replacement succeeds.
     *
     * @param values
     *         new values of the settings; surrounding whitespace is removed
     * @throws IllegalArgumentException
     *         if a setting is not owned by this file, or a value is blank for a mandatory setting
     * @throws UncheckedIOException
     *         if the file cannot be written
     */
    synchronized void setValues( @Nonnull SystemPropertyValues values ) {
        requireNonNull( values, "values must not be null" );

        Map< String, String > changes = new LinkedHashMap<>();
        for ( Map.Entry< SystemPropertyIf, String > entry : values.asMap().entrySet() ) {
            String name = requireManaged( entry.getKey() );
            changes.put( name, normalize( entry.getKey(), entry.getValue() ) );
        }
        if ( changes.isEmpty() ) {
            return;
        }

        applyAndSave( changes );
        log.info( "Updated {} vDeck settings in file {}", changes.size(), propertiesFile );
    }

    private void applyAndSave( @Nonnull Map< String, String > changes ) {
        Properties updated = new Properties();
        updated.putAll( properties );
        changes.forEach( updated::setProperty );
        save( updated );
        properties = updated;
    }

    private static @Nonnull String normalize( @Nonnull SystemPropertyIf property, @Nonnull String value ) {
        String strippedValue = value.strip();
        if ( strippedValue.isEmpty() && property.isMandatory() ) {
            throw new IllegalArgumentException(
                    "value must not be blank for mandatory property '%s'".formatted( property.getName() ) );
        }
        return strippedValue;
    }

    private void loadOrCreate() {
        if ( !Files.exists( propertiesFile ) ) {
            createParentDirectories();
            defaults.forEach( ( property, defaultValue ) -> properties.setProperty( property.getName(), defaultValue ) );
            save( properties );
            log.info( "Created vDeck settings file {} with {} default values", propertiesFile, properties.size() );
            return;
        }

        read();
        if ( restoreMissingValues() ) {
            save( properties );
            log.info( "Loaded vDeck settings from {} and restored missing values", propertiesFile );
            return;
        }
        log.info( "Loaded vDeck settings from {}", propertiesFile );
    }

    private void createParentDirectories() {
        Path directory = propertiesFile.getParent();
        try {
            Files.createDirectories( directory );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to create vDeck settings directory " + directory, exception );
        }
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
        for ( Map.Entry< SystemPropertyIf, String > entry : defaults.entrySet() ) {
            SystemPropertyIf property = entry.getKey();
            String value = properties.getProperty( property.getName() );
            boolean missing = value == null || ( value.isEmpty() && property.isMandatory() );
            if ( missing ) {
                properties.setProperty( property.getName(), entry.getValue() );
                log.warn( "Restored default value of vDeck setting '{}' missing in file {}",
                        property.getName(), propertiesFile );
                restored = true;
            }
        }
        return restored;
    }

    private void save( @Nonnull Properties values ) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile( propertiesFile.getParent(), "vDeck", ".properties.tmp" );
            try ( BufferedWriter writer = Files.newBufferedWriter( temporaryFile, StandardCharsets.UTF_8 ) ) {
                values.store( writer, FILE_HEADER );
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

    private @Nonnull String requireManaged( @Nullable SystemPropertyIf property ) {
        requireNonNull( property, "property must not be null" );
        if ( !defaults.containsKey( property ) ) {
            throw new IllegalArgumentException(
                    "Property '%s' is not managed by file %s".formatted( property.getName(), propertiesFile ) );
        }
        return property.getName();
    }

    private static @Nonnull Map< SystemPropertyIf, String > toDefaults( @Nonnull SystemPropertyValues defaultValues ) {
        Map< SystemPropertyIf, String > defaults = new LinkedHashMap<>();
        defaultValues.asMap().forEach( ( property, defaultValue ) -> {
            String name = property.getName();
            if ( name == null || name.isBlank() ) {
                throw new IllegalArgumentException( "defaultValues must not contain a property with a blank name" );
            }
            if ( property.isMandatory() && defaultValue.isBlank() ) {
                throw new IllegalArgumentException(
                        "Default value of mandatory property '%s' must not be blank".formatted( name ) );
            }
            defaults.put( property, defaultValue.strip() );
        } );
        return Collections.unmodifiableMap( defaults );
    }
}
