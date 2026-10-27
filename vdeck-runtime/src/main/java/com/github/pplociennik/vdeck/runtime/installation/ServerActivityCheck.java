package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Decides whether a Valheim server of this installation may be running, in which case no update is
 * applied (AD-024). A recovery token in {@code .vDeck/server.pid} or a live {@code valheim_server.exe} of
 * this installation counts as activity, and so does any failure of the check: an update must never start
 * on an uncertain answer. The panel may still offer adoption of a surviving server (AD-017).
 */
@Log4j2
public final class ServerActivityCheck {

    private final Path installation;

    private final Supplier< Stream< ProcessHandle > > processes;

    /**
     * Creates the check for an installation using the operating system's process list.
     *
     * @param installation
     *         canonical installation root
     * @throws NullPointerException
     *         if the installation is null
     */
    public ServerActivityCheck( @Nonnull Path installation ) {
        this( installation, ProcessHandle::allProcesses );
    }

    /**
     * Creates the check with an explicit process source.
     *
     * @param installation
     *         canonical installation root
     * @param processes
     *         source of the running processes
     */
    ServerActivityCheck( @Nonnull Path installation, @Nonnull Supplier< Stream< ProcessHandle > > processes ) {
        this.installation = requireNonNull( installation, "installation must not be null" );
        this.processes = requireNonNull( processes, "processes must not be null" );
    }

    /**
     * Checks for a recovery token or a running server executable of this installation.
     * Reads the file system and the process list.
     *
     * @return {@code true} when a server may be running or the check failed
     */
    public boolean isServerPossiblyActive() {
        try {
            if ( Files.exists( installation.resolve( UpdateLayout.PANEL_DIRECTORY ).resolve( "server.pid" ),
                    NOFOLLOW_LINKS ) ) {
                log.info( "A recovery token exists; the server may still be running" );
                return true;
            }
            Path executable = installation.resolve( "valheim_server.exe" );
            try ( Stream< ProcessHandle > running = processes.get() ) {
                boolean active = running.map( handle -> handle.info().command() ).flatMap( Optional::stream )
                        .anyMatch( command -> isSameFile( Path.of( command ), executable ) );
                if ( active ) {
                    log.info( "A server process of this installation is running" );
                }
                return active;
            }
        } catch ( RuntimeException exception ) {
            log.warn( "Could not determine whether the server is running; treating it as active", exception );
            return true;
        }
    }

    private static boolean isSameFile( Path command, Path executable ) {
        if ( !command.getFileName().toString().equalsIgnoreCase( executable.getFileName().toString() ) ) {
            return false;
        }
        try {
            return command.toRealPath().equals( executable.toRealPath() );
        } catch ( IOException exception ) {
            return command.normalize().toString().equalsIgnoreCase( executable.normalize().toString() );
        }
    }
}
