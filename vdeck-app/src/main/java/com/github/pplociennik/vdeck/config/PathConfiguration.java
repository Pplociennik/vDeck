package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.VdeckApplication;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.impl.PathServiceImpl;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared path resolution service.
 * {@link ApplicationHome} is not published as a bean because it is a construction detail of
 * {@link PathServiceImpl} rather than a collaborator anything else needs.
 */
@Configuration( proxyBeanMethods = false )
class PathConfiguration {

    /**
     * Creates the service resolving configured paths against the vDeck installation directory.
     *
     * @param configuredDirectory
     *         value of the {@code vdeck.server-directory} override, empty when unset
     * @return path service bound to the resolved installation directory
     */
    @Bean
    @Nonnull
    PathService pathService( @Value( "${vdeck.server-directory:}" ) @Nullable String configuredDirectory ) {
        return new PathServiceImpl( new ApplicationHome( VdeckApplication.class ), configuredDirectory );
    }
}
