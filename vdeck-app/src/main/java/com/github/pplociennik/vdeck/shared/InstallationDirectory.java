package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import jakarta.annotation.Nonnull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * Resolves the installation directory for bootstrap code that runs in {@code VdeckApplication.main()}
 * before the Spring context, and therefore before {@code PathService}, exists. The directory is the
 * {@code vdeck.server-directory} override or, without it, the application home; it is neither
 * canonicalized nor checked here.
 */
public final class InstallationDirectory {

    private InstallationDirectory() {
    }

    /**
     * Returns the installation directory selected by the system properties.
     *
     * @param systemProperties
     *         system properties carrying the optional installation directory override
     * @param applicationHome
     *         directory used when no override is set
     * @return the override, or the application home without one
     * @throws NullPointerException
     *         if an argument is null
     * @throws InvalidPathException
     *         if the override is not a valid path
     */
    public static @Nonnull Path resolve( @Nonnull Properties systemProperties, @Nonnull Path applicationHome ) {
        requireNonNull( systemProperties, "systemProperties must not be null" );
        requireNonNull( applicationHome, "applicationHome must not be null" );
        String configured = systemProperties.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY, "" ).strip();
        return configured.isEmpty() ? applicationHome : Path.of( configured );
    }
}
