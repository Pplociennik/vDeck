package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Set of values for settings of one properties file, handed to the properties service as one argument
 * instead of a loose map: the default values a file is loaded with, or the values written together in
 * one atomic save.
 *
 * <p>An instance is created empty by {@link #create()} and filled with {@link #with}, and describes a
 * single load or save: a new instance is built for every call and is never shared, so it is not
 * thread-safe. Entries keep their insertion order. Every entry has a non-null setting and value, and a
 * setting appears at most once; whether a value is acceptable for its setting is decided by the file
 * that receives it.
 *
 * <p>Equality compares the entries. There is deliberately no generated {@code toString}, because a value
 * may be a secret such as the server password.
 */
@EqualsAndHashCode
public final class SystemPropertyValues {

    private final Map< SystemPropertyIf, String > values = new LinkedHashMap<>();

    private SystemPropertyValues() {
    }

    /**
     * Creates an empty set of setting values.
     *
     * @return new set holding no entry
     */
    public static @Nonnull SystemPropertyValues create() {
        return new SystemPropertyValues();
    }

    /**
     * Adds the value of one setting to this set.
     *
     * @param property
     *         setting the value belongs to
     * @param value
     *         value of the setting, as it is to be stored; may be empty
     * @return this set, so that entries can be added in a chain
     * @throws IllegalArgumentException
     *         if the set already holds a value for the setting
     */
    public @Nonnull SystemPropertyValues with( @Nonnull SystemPropertyIf property, @Nonnull String value ) {
        requireNonNull( property, "property must not be null" );
        requireNonNull( value, "value must not be null" );
        if ( values.containsKey( property ) ) {
            throw new IllegalArgumentException(
                    "Property '%s' already has a value in this set".formatted( property.getName() ) );
        }
        values.put( property, value );
        return this;
    }

    /**
     * Returns the entries of this set.
     *
     * @return read-only view of the entries in insertion order; it reflects entries added later
     */
    public @Nonnull Map< SystemPropertyIf, String > asMap() {
        return Collections.unmodifiableMap( values );
    }
}
