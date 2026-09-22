package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Serves every external properties file of the panel through a single instance. Each file is resolved
 * inside the configuration directory by {@link PathService#getConfigurationFile(String)} (AD-010) and
 * handled by its own {@link PropertiesFile}, kept under the file name it was loaded with.
 *
 * <p>Files are loaded during application startup and read and written by request threads afterwards;
 * the concurrent map publishes every loaded file safely to those threads. The file contents themselves
 * follow the rules of {@link PropertiesFile}.
 */
@Log4j2
@Service
class SystemPropertiesServiceImpl implements SystemPropertiesService {

    private final PathService pathService;

    /**
     * Loaded files keyed by the bare file name they were loaded with.
     */
    private final Map< String, PropertiesFile > files = new ConcurrentHashMap<>();

    /**
     * Creates the service with no file loaded yet.
     *
     * @param pathService
     *         service resolving every properties file inside the configuration directory
     */
    SystemPropertiesServiceImpl( @Nonnull PathService pathService ) {
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    @Override
    public void loadFile( @Nonnull String fileName, @Nonnull SystemPropertyValues defaultValues ) {
        requireNonNull( fileName, "fileName must not be null" );
        requireNonNull( defaultValues, "defaultValues must not be null" );
        if ( files.containsKey( fileName ) ) {
            throw new IllegalStateException( "Properties file '%s' has already been loaded".formatted( fileName ) );
        }

        Path propertiesFile = pathService.getConfigurationFile( fileName );
        log.debug( "Loading vDeck settings file '{}' from {}", fileName, propertiesFile );
        files.put( fileName, new PropertiesFile( propertiesFile, defaultValues ) );
    }

    @Override
    public @Nonnull String getValue( @Nonnull String fileName, @Nonnull SystemPropertyIf property ) {
        return requireLoaded( fileName ).getValue( property );
    }

    @Override
    public void setValue( @Nonnull String fileName, @Nonnull SystemPropertyIf property, @Nonnull String value ) {
        requireLoaded( fileName ).setValue( property, value );
    }

    @Override
    public void setValues( @Nonnull String fileName, @Nonnull SystemPropertyValues values ) {
        requireLoaded( fileName ).setValues( values );
    }

    private @Nonnull PropertiesFile requireLoaded( @Nonnull String fileName ) {
        requireNonNull( fileName, "fileName must not be null" );
        PropertiesFile file = files.get( fileName );
        if ( file == null ) {
            throw new IllegalStateException( "Properties file '%s' has not been loaded".formatted( fileName ) );
        }
        return file;
    }
}
