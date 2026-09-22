package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Reads typed values of settings through {@link SystemPropertiesService}. A settings file can be edited
 * by hand, so a stored value may not match its setting; every reader then falls back to a value given by
 * the caller and logs the setting's name at {@code WARN}, never the stored value itself. The file is
 * never rewritten by a read.
 *
 * <p>The readers are static and stateless: everything they need is passed as parameters, so every
 * service reading its settings uses the same interpretation of a stored value.
 */
@Log4j2
public final class PropertyReader {

    private PropertyReader() {
    }

    /**
     * Returns the stored value of a setting as it is.
     *
     * @param service
     *         service holding the loaded settings file
     * @param fileName
     *         name of the loaded file owning the setting
     * @param property
     *         setting to read
     * @return stored value, possibly empty for an optional setting
     */
    public static @Nonnull String read( @Nonnull SystemPropertiesService service, @Nonnull String fileName,
                                        @Nonnull SystemPropertyIf property ) {
        requireNonNull( service, "service must not be null" );
        requireNonNull( fileName, "fileName must not be null" );
        requireNonNull( property, "property must not be null" );
        return service.getValue( fileName, property );
    }

    /**
     * Returns the stored value of a setting as a number.
     *
     * @param service
     *         service holding the loaded settings file
     * @param fileName
     *         name of the loaded file owning the setting
     * @param property
     *         setting to read
     * @param defaultValue
     *         number, in its stored form, returned when the stored value is not a number
     * @return stored number, or the default when the stored value is not a number
     * @throws NumberFormatException
     *         if the default itself is not a number
     */
    public static int readInt( @Nonnull SystemPropertiesService service, @Nonnull String fileName,
                               @Nonnull SystemPropertyIf property, @Nonnull String defaultValue ) {
        requireNonNull( defaultValue, "defaultValue must not be null" );
        String value = read( service, fileName, property );
        try {
            return Integer.parseInt( value );
        } catch ( NumberFormatException exception ) {
            log.warn( "Setting '{}' of file {} is not a number; using its default", property.getName(), fileName );
            return Integer.parseInt( defaultValue );
        }
    }

    /**
     * Returns the stored value of a setting as a boolean. Only {@code true} and {@code false}, in any letter
     * case, are accepted.
     *
     * @param service
     *         service holding the loaded settings file
     * @param fileName
     *         name of the loaded file owning the setting
     * @param property
     *         setting to read
     * @param defaultValue
     *         boolean, in its stored form, returned when the stored value is neither {@code true} nor
     *         {@code false}; any text other than {@code true} counts as {@code false}
     * @return stored boolean, or the default when the stored value is not a boolean
     */
    public static boolean readBoolean( @Nonnull SystemPropertiesService service, @Nonnull String fileName,
                                       @Nonnull SystemPropertyIf property, @Nonnull String defaultValue ) {
        requireNonNull( defaultValue, "defaultValue must not be null" );
        String value = read( service, fileName, property );
        if ( "true".equalsIgnoreCase( value ) || "false".equalsIgnoreCase( value ) ) {
            return Boolean.parseBoolean( value );
        }
        log.warn( "Setting '{}' of file {} is not true or false; using its default", property.getName(), fileName );
        return Boolean.parseBoolean( defaultValue );
    }

    /**
     * Returns the stored value of an optional setting interpreted by the given parser. An empty value means
     * the setting is not used; a value the parser does not recognise is ignored as if it were empty.
     *
     * @param service
     *         service holding the loaded settings file
     * @param fileName
     *         name of the loaded file owning the setting
     * @param property
     *         setting to read
     * @param parser
     *         interpretation of a non-empty stored value; returns empty for a value it does not recognise
     * @param <T>
     *         type of the interpreted value
     * @return interpreted value, or empty when the stored value is empty or not recognised
     */
    public static @Nonnull < T > Optional< T > readOptional( @Nonnull SystemPropertiesService service,
                                                             @Nonnull String fileName,
                                                             @Nonnull SystemPropertyIf property,
                                                             @Nonnull Function< String, Optional< T > > parser ) {
        requireNonNull( parser, "parser must not be null" );
        String value = read( service, fileName, property );
        if ( value.isEmpty() ) {
            return Optional.empty();
        }
        Optional< T > parsed = requireNonNull( parser.apply( value ), "parser must not return null" );
        if ( parsed.isEmpty() ) {
            log.warn( "Setting '{}' of file {} holds an unknown value; ignoring it", property.getName(), fileName );
        }
        return parsed;
    }
}
