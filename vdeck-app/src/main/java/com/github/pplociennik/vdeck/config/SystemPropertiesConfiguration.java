package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import com.github.pplociennik.vdeck.shared.service.impl.SystemPropertiesServiceImpl;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the service handling the external {@code vDeck.properties} settings file.
 */
@Configuration( proxyBeanMethods = false )
class SystemPropertiesConfiguration {

    /**
     * Creates the service owning {@code vDeck.properties} in the installation directory.
     * The file is created with default values when it does not exist yet.
     *
     * @param pathService
     *         service resolving the settings file inside the installation directory
     * @return service handling the vDeck settings file
     */
    @Bean
    @Nonnull
    SystemPropertiesService vdeckProperties( @Nonnull PathService pathService ) {
        return new SystemPropertiesServiceImpl( pathService.getAbsolutePath( VdeckProperty.FILE_NAME ),
                VdeckProperty.defaults() );
    }
}
