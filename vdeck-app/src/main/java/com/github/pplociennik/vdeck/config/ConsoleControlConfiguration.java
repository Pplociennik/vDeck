package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.windows.JvmConsoleControlSender;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** Composes the idle signal sender without starting a helper process or allocating a waiting thread. */
@Configuration( proxyBeanMethods = false )
class ConsoleControlConfiguration {

    /** Runner artifact location relative to the installation, matching the distribution layout. */
    private static final String RUNNER_JAR = "lib/vdeck-runner.jar";

    /**
     * Creates the sender using the runner artifact in the installation's {@code lib} directory,
     * including during IDE startup.
     * The runtime invokes it on its stop worker after completing the required snapshot.
     *
     * @param paths
     *         installation-confined path resolver
     * @return singleton boundary that starts one short-lived helper for each signal request
     * @throws NullPointerException
     *         if paths is null
     */
    @Bean
    @Nonnull
    ConsoleControlSender consoleControlSender( @Nonnull PathService paths ) {
        requireNonNull( paths, "paths must not be null" );
        Path runner = paths.getAbsolutePath( RUNNER_JAR );
        String java = Path.of( System.getProperty( "java.home" ), "bin", "javaw.exe" ).toString();
        return new JvmConsoleControlSender( List.of(
                java, "--enable-native-access=ALL-UNNAMED", "-jar", runner.toString() ) );
    }
}
