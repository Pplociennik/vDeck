package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.shutdown.service.PanelShutdownService;
import com.github.pplociennik.vdeck.feature.shutdown.service.impl.PanelShutdownServiceImpl;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Objects.requireNonNull;

/**
 * Composes the panel shutdown requested from the side menu: the exit closes the Spring context and then ends
 * the process with the exit code Spring reports, which the launcher returns to the start script.
 */
@Log4j2
@Configuration( proxyBeanMethods = false )
class PanelShutdownConfiguration {

    /**
     * Name of the thread closing the panel, visible in the panel log.
     */
    private static final String SHUTDOWN_THREAD_NAME = "vdeck-panel-shutdown";

    /**
     * Creates the service closing the panel on its own non-daemon thread.
     *
     * <p>The thread must not be a daemon: Tomcat's request threads are, and once the web server has stopped
     * nothing else would keep the JVM alive while the close strategy is still stopping the server.
     *
     * @param applicationContext
     *         context of the running panel, closed by the shutdown
     * @return service starting the shutdown at most once
     * @throws NullPointerException
     *         if {@code applicationContext} is {@code null}
     */
    @Bean
    @Nonnull
    PanelShutdownService panelShutdownService( @Nonnull ConfigurableApplicationContext applicationContext ) {
        requireNonNull( applicationContext, "applicationContext must not be null" );
        return new PanelShutdownServiceImpl( () -> exitPanel( applicationContext ),
                task -> Thread.ofPlatform().name( SHUTDOWN_THREAD_NAME ).daemon( false ).start( task ) );
    }

    /**
     * Closes the context, which runs every {@code @PreDestroy} including the close strategy, and ends the
     * process. {@code System.exit} is still needed afterwards: a non-daemon thread left behind by a library
     * would otherwise keep the panel process alive.
     */
    private static void exitPanel( ConfigurableApplicationContext applicationContext ) {
        int exitCode = SpringApplication.exit( applicationContext );
        log.info( "Panel closed from the side menu; exiting with code {}", exitCode );
        System.exit( exitCode );
    }
}
