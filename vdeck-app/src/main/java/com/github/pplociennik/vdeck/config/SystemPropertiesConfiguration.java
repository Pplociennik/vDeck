package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import com.github.pplociennik.vdeck.shared.service.impl.SystemPropertiesServiceImpl;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires one service per external properties file. Every file lives in the configuration directory of
 * the installation (AD-010), which {@link PathService#getConfigurationFile(String)} enforces, and every
 * service bean is named after the qualifier its property enum declares, because consumers select the
 * file they need by that qualifier.
 */
@Configuration( proxyBeanMethods = false )
class SystemPropertiesConfiguration {

    /**
     * Creates the service owning {@code vDeck.properties}, the panel's own settings.
     * The file and its directory are created with default values when they do not exist yet.
     *
     * @param pathService
     *         service resolving the settings file inside the configuration directory
     * @return service handling the vDeck settings file
     */
    @Bean( VdeckProperty.SERVICE_QUALIFIER )
    @Nonnull
    SystemPropertiesService vdeckProperties( @Nonnull PathService pathService ) {
        return new SystemPropertiesServiceImpl( pathService.getConfigurationFile( VdeckProperty.FILE_NAME ),
                VdeckProperty.defaults() );
    }

    /**
     * Creates the service owning {@code valheim-server.properties}, the launch profile of the Valheim
     * server. The file and its directory are created with default values when they do not exist yet.
     *
     * @param pathService
     *         service resolving the settings file inside the configuration directory
     * @return service handling the launch profile file
     */
    @Bean( LaunchProperty.SERVICE_QUALIFIER )
    @Nonnull
    SystemPropertiesService launchProperties( @Nonnull PathService pathService ) {
        return new SystemPropertiesServiceImpl( pathService.getConfigurationFile( LaunchProperty.FILE_NAME ),
                LaunchProperty.defaults() );
    }
}