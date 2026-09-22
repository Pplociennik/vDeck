package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import static java.util.Objects.requireNonNull;

/**
 * Loads every external properties file of the panel into {@link SystemPropertiesService} during startup,
 * creating missing files with their default values and restoring missing keys.
 *
 * <p>Loading runs once all singletons exist, which is before the embedded web server starts, so no
 * request can reach a file that has not been loaded. A failure is propagated and aborts the start,
 * because the panel cannot work without its settings.
 */
@Log4j2
@Component
class SystemPropertiesInitializer implements SmartInitializingSingleton {

    private final SystemPropertiesService systemPropertiesService;

    /**
     * Creates the initializer loading the files into the given service.
     *
     * @param systemPropertiesService
     *         service every properties file is loaded into
     */
    SystemPropertiesInitializer( @Nonnull SystemPropertiesService systemPropertiesService ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
    }

    /**
     * Loads {@code vDeck.properties}, the panel's own settings, and {@code valheim-server.properties},
     * the launch profile of the Valheim server. Reads and may write the configuration directory.
     */
    @Override
    public void afterSingletonsInstantiated() {
        systemPropertiesService.loadFile( VdeckProperty.FILE_NAME, VdeckProperty.defaults() );
        systemPropertiesService.loadFile( LaunchProperty.FILE_NAME, LaunchProperty.defaults() );
        log.info( "vDeck settings files loaded" );
    }
}
