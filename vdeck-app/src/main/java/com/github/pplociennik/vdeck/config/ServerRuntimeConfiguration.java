package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.lifecycle.ServerStateMachine;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import com.github.pplociennik.vdeck.runtime.windows.WindowsProcessLauncher;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the server runtime, the component that owns the managed Valheim process.
 *
 * <p>The runtime is a singleton on purpose: vDeck manages at most one server, and the single instance
 * is the only place in the application holding its lifecycle state. Consumers inject
 * {@link ServerRuntime}; nobody injects the state machine or the launcher.
 *
 * <p>The bean's {@code close()} is called by Spring when the context shuts down, which stops the
 * runtime's event queue. The server process itself is ended by the operating system, because the
 * launcher places it in a job object tied to this process's lifetime — that is what also covers a
 * panel that is killed or crashes, where no Spring callback runs at all.
 */
@Configuration( proxyBeanMethods = false )
class ServerRuntimeConfiguration {

    /**
     * Creates the boundary used to create the Valheim process. Windows is the only supported platform
     * today; a second one would select its implementation here.
     *
     * @return launcher creating the server process
     */
    @Bean
    @Nonnull
    GameProcessLauncher gameProcessLauncher() {
        return new WindowsProcessLauncher();
    }

    /**
     * Creates the single owner of the managed server's lifecycle state.
     *
     * @param gameProcessLauncher
     *         boundary used to create the server process
     * @return the application's only server runtime
     */
    @Bean
    @Nonnull
    ServerRuntime serverRuntime( @Nonnull GameProcessLauncher gameProcessLauncher ) {
        return new ServerStateMachine( gameProcessLauncher );
    }
}