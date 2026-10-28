package com.github.pplociennik.vdeck.config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Opens an address in the user's default Windows browser.
 *
 * <p>{@code java.awt.Desktop} is unavailable because Spring Boot runs headless, so the address is handed to
 * the Windows URL handler {@code rundll32.exe url.dll,FileProtocolHandler}. The executable is resolved from
 * {@code %SystemRoot%\System32} rather than {@code PATH} or the working directory, which is the
 * installation directory, and the address is passed as a separate process argument without a shell.
 */
@Log4j2
class SystemBrowser {

    /**
     * Entry point of the Windows URL handler that opens an address with the default browser.
     */
    private static final String URL_HANDLER = "url.dll,FileProtocolHandler";

    /**
     * Windows directory from {@code SystemRoot}; {@code null} when the variable is not set.
     */
    private final String systemRoot;

    /**
     * Creates the browser using the {@code SystemRoot} environment variable.
     */
    SystemBrowser() {
        this( System.getenv( "SystemRoot" ) );
    }

    /**
     * Creates the browser using the given Windows directory.
     *
     * @param systemRoot
     *         Windows directory holding {@code System32}, or {@code null} when it is unknown
     */
    SystemBrowser( @Nullable String systemRoot ) {
        this.systemRoot = systemRoot;
    }

    /**
     * Asks Windows to open the address in the default browser without waiting for the browser.
     *
     * @param address
     *         address to open
     * @throws NullPointerException
     *         if the address is null
     * @throws IOException
     *         if the Windows directory is unknown or the URL handler cannot be started
     */
    void open( @Nonnull URI address ) throws IOException {
        requireNonNull( address, "address must not be null" );
        if ( systemRoot == null ) {
            throw new IOException( "The SystemRoot environment variable is not set" );
        }

        Path handler = Path.of( systemRoot, "System32", "rundll32.exe" );
        log.debug( "Opening {} with {}", address, handler );
        new ProcessBuilder( handler.toString(), URL_HANDLER, address.toString() )
                .redirectOutput( Redirect.DISCARD )
                .redirectError( Redirect.DISCARD )
                .start();
    }
}
