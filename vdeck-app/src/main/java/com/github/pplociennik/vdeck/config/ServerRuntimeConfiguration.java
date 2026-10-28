package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.installation.InstallationLock;
import com.github.pplociennik.vdeck.runtime.lifecycle.ManagedLaunchStore;
import com.github.pplociennik.vdeck.runtime.lifecycle.ServerStateMachine;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.spi.GameProcessLauncher;
import com.github.pplociennik.vdeck.runtime.windows.WindowsFileIdentityReader;
import com.github.pplociennik.vdeck.runtime.windows.WindowsProcessLauncher;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Assembles the server runtime, the component that owns the managed Valheim process.
 *
 * <p>The runtime is a singleton on purpose: vDeck manages at most one server, and the single instance
 * is the only place in the application holding its lifecycle state. Consumers inject
 * {@link ServerRuntime}; nobody injects the state machine or the launcher.
 *
 * <p>ServerShutdownCoordinator first applies the configured panel close strategy (AD-031). Runtime bean
 * destruction then releases resources without killing a surviving server; the durable token supports
 * explicit recovery.
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
     * @param paths
     *         authorized installation paths
     * @param consoleControlSender
     *         idle bean that launches an isolated helper only when a signal is requested
     * @param installationLock
     *         lock taken over from the launcher before Spring started; empty when the panel runs without
     *         the launcher, and the store then acquires the lock itself
     * @return the application's only server runtime
     * @throws IllegalStateException
     *         if the taken-over lock belongs to another installation, or another process owns it
     */
    @Bean
    @Nonnull
    ServerRuntime serverRuntime( @Nonnull GameProcessLauncher gameProcessLauncher,
                                 @Nonnull PathService paths, @Nonnull ConsoleControlSender consoleControlSender,
                                 @Nonnull Optional< InstallationLock > installationLock ) {
        ManagedLaunchStore store = installationLock.map( lock -> storeFor( lock, paths ) )
                .orElseGet( () -> new ManagedLaunchStore( paths.getBaseDirectory() ) );
        try {
            return new ServerStateMachine( gameProcessLauncher,
                    new FileWorldSnapshotStore( paths.getBaseDirectory(), new WindowsFileIdentityReader() ),
                    consoleControlSender, store );
        } catch ( RuntimeException exception ) {
            store.close();
            throw exception;
        }
    }

    private static ManagedLaunchStore storeFor( InstallationLock lock, PathService paths ) {
        if ( !lock.getInstallation().equals( paths.getBaseDirectory() ) ) {
            lock.close();
            throw new IllegalStateException( "The launcher handed over the lock of another installation" );
        }
        return new ManagedLaunchStore( lock );
    }
}
