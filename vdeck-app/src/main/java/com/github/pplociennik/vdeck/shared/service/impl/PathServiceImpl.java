package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.system.ApplicationHome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Resolves configured relative paths against the vDeck installation directory.
 * This is the only component allowed to turn a configured path string into a {@link Path}, so that
 * normalization and containment checks are applied consistently everywhere in the panel.
 *
 * <p>The base directory is the directory holding {@code valheim_server.exe}. It is derived from
 * {@link ApplicationHome} unless the {@code vdeck.server-directory} override is configured, which is
 * what the distribution's start script (its JARs live in {@code lib}), IDE runs and manual test
 * installations use. Resolution happens once,
 * during construction, so a misconfigured directory aborts the panel start instead of failing later.
 *
 * <p>Instances are immutable after construction and perform no writes. Every method touches the file
 * system only to read metadata and never creates directories or files.
 */
@Log4j2
public class PathServiceImpl implements PathService {

    private final ApplicationHome applicationHome;

    private final Path baseDirectory;

    /**
     * Creates a service resolving paths against the vDeck installation directory.
     * Reads the file system to resolve the base directory to its real form.
     *
     * @param applicationHome
     *         application home used when no explicit directory is configured
     * @param configuredDirectory
     *         value of the {@code vdeck.server-directory} override; {@code null}
     *         and blank both mean that no override was configured
     * @throws PathResolutionException
     *         if the resolved base directory is not a directory
     * @throws UncheckedIOException
     *         if the resolved base directory does not exist or cannot be read
     */
    public PathServiceImpl( @Nonnull ApplicationHome applicationHome, @Nullable String configuredDirectory ) {
        this.applicationHome = requireNonNull( applicationHome, "applicationHome must not be null" );
        this.baseDirectory = resolveBaseDirectory( configuredDirectory );
        log.info( "Base directory resolved to {}", this.baseDirectory );
    }

    /**
     * Returns the vDeck installation directory in its real, absolute form.
     *
     * @return the base directory every relative path is resolved against
     */
    @Override
    public @Nonnull Path getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * Resolves a relative path against the installation directory and validates that it stays inside it.
     * Reads file system metadata but never creates the target, so the returned path may not exist yet.
     *
     * <p>The value is rejected when it is absolute, syntactically invalid, or when it escapes the
     * installation directory — directly, through {@code ..} segments, or through a symbolic link or a
     * Windows junction pointing outside. When the target already exists the returned path is its real
     * form, with links already followed.
     *
     * @param relativePath
     *         configured path relative to the installation directory
     * @return absolute path inside the installation directory
     *
     * @throws IllegalArgumentException
     *         if the value is blank
     * @throws PathResolutionException
     *         if the value is absolute, invalid, or leaves the installation directory
     * @throws UncheckedIOException
     *         if the real form of an existing target cannot be read
     */
    @Override
    public @Nonnull Path getAbsolutePath( @Nonnull String relativePath ) {
        requireNonNull( relativePath, "relativePath must not be null" );
        String strippedPath = relativePath.strip();
        if ( strippedPath.isEmpty() ) {
            throw new IllegalArgumentException( "relativePath must not be blank" );
        }

        Path candidate = toPath( strippedPath );
        if ( candidate.isAbsolute() ) {
            throw new PathResolutionException(
                    "Configured path '%s' must be relative to the installation directory".formatted( strippedPath ) );
        }

        Path resolved = baseDirectory.resolve( candidate ).normalize();
        requireInsideBaseDirectory( resolved, strippedPath );
        if ( !Files.exists( resolved ) ) {
            return resolved;
        }

        Path realPath = toRealPath( resolved );
        requireInsideBaseDirectory( realPath, strippedPath );
        return realPath;
    }

    /**
     * Resolves a properties file inside the configuration directory of the installation.
     * Reads file system metadata but never creates the directory or the file.
     *
     * @param fileName
     *         bare file name, without any directory part
     * @return absolute path of the file inside the configuration directory
     *
     * @throws IllegalArgumentException
     *         if the name is blank
     * @throws PathResolutionException
     *         if the name contains a directory part, is {@code .} or {@code ..}, or the resolved path
     *         leaves the installation directory
     * @throws UncheckedIOException
     *         if the real form of an existing target cannot be read
     */
    @Override
    public @Nonnull Path getConfigurationFile( @Nonnull String fileName ) {
        requireNonNull( fileName, "fileName must not be null" );
        String strippedName = fileName.strip();
        if ( strippedName.isEmpty() ) {
            throw new IllegalArgumentException( "fileName must not be blank" );
        }
        if ( strippedName.contains( "/" ) || strippedName.contains( "\\" )
                || strippedName.equals( "." ) || strippedName.equals( ".." ) ) {
            throw new PathResolutionException(
                    "Configuration file name '%s' must be a bare file name".formatted( strippedName ) );
        }
        return getAbsolutePath( CONFIGURATION_DIRECTORY + "/" + strippedName );
    }

    private @Nonnull Path resolveBaseDirectory( @Nullable String configuredDirectory ) {
        boolean overridden = configuredDirectory != null && !configuredDirectory.isBlank();
        Path configured = overridden
                ? toPath( configuredDirectory.strip() )
                : applicationHome.getDir().toPath();

        Path realPath = toRealPath( configured );
        if ( !Files.isDirectory( realPath ) ) {
            throw new PathResolutionException( "Base directory '%s' is not a directory".formatted( realPath ) );
        }
        log.debug( "Base directory taken from {}", overridden ? "vdeck.server-directory override" : "application home" );
        return realPath;
    }

    private void requireInsideBaseDirectory( @Nonnull Path resolved, @Nonnull String configuredValue ) {
        if ( !resolved.startsWith( baseDirectory ) ) {
            throw new PathResolutionException(
                    "Configured path '%s' resolves outside the installation directory".formatted( configuredValue ) );
        }
    }

    private static @Nonnull Path toPath( @Nonnull String value ) {
        try {
            return Path.of( value );
        } catch ( InvalidPathException exception ) {
            throw new PathResolutionException( "Configured path '%s' is not a valid path".formatted( value ),
                    exception );
        }
    }

    private static @Nonnull Path toRealPath( @Nonnull Path path ) {
        try {
            return path.toRealPath();
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Failed to resolve the real path of " + path, exception );
        }
    }
}
