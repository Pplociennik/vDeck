package com.github.pplociennik.vdeck.feature.backup.service.impl;

import com.github.pplociennik.vdeck.feature.backup.service.BackupPathService;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/** Resolves the configured snapshot directory directly below the panel's own {@code .vDeck} directory. */
@Log4j2
@Service
class BackupPathServiceImpl implements BackupPathService {

    private static final String VDECK_DIRECTORY = ".vDeck";

    private final PathService paths;

    private final SystemPropertiesService systemPropertiesService;

    /**
     * Creates the backup resolver.
     *
     * @param paths
     *         installation path resolver
     * @param systemPropertiesService
     *         source of the configured backup directory name
     * @throws NullPointerException
     *         if an argument is null
     */
    BackupPathServiceImpl( @Nonnull PathService paths, @Nonnull SystemPropertiesService systemPropertiesService ) {
        this.paths = requireNonNull( paths, "paths must not be null" );
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull Path resolveRoot( @Nonnull Path saveDirectory ) {
        requireNonNull( saveDirectory, "saveDirectory must not be null" );
        String name = read( systemPropertiesService, VdeckProperty.FILE_NAME, VdeckProperty.SNAPSHOT_DIRECTORY );
        Path boundary = paths.getBaseDirectory().resolve( VDECK_DIRECTORY );
        Path root = paths.getAbsolutePath( VDECK_DIRECTORY + "/" + name );
        if ( !boundary.equals( root.getParent() ) ) {
            throw new IllegalStateException( "Snapshot directory must be a single directory below .vDeck" );
        }
        Path save = saveDirectory.toAbsolutePath().normalize();
        if ( root.startsWith( save ) || save.startsWith( root ) ) {
            throw new IllegalStateException( "Snapshot directory overlaps the active world" );
        }
        try {
            for ( Path part = root; part.startsWith( boundary ); part = part.getParent() ) {
                if ( !Files.exists( part, NOFOLLOW_LINKS ) ) {
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes( part, BasicFileAttributes.class, NOFOLLOW_LINKS );
                if ( !attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()
                        || !part.toRealPath().equals( part ) ) {
                    throw new IOException( "Snapshot directory chain redirects or is not a directory" );
                }
            }
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Unsafe backup root", exception );
        }
        log.debug( "Resolved the backup root inside the panel's own directory" );
        return root;
    }
}
