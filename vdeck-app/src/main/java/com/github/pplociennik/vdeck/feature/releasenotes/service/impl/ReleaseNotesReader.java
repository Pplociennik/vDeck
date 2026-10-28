package com.github.pplociennik.vdeck.feature.releasenotes.service.impl;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Reads the change list files shipped inside the application, {@code release-notes/<version>.md}.
 *
 * <p>A file that is badly named or breaks the format is skipped with a warning instead of breaking the
 * home page; {@code ReleaseNotesFilesTest} keeps such files out of a build. Nothing is cached: the files
 * are read only while an installed version waits for acknowledgement.
 */
@Log4j2
@Component
class ReleaseNotesReader {

    /**
     * Classpath location of the change list files.
     */
    static final String LOCATION_PATTERN = "classpath*:release-notes/*.md";

    /**
     * Extension that follows the version in a change list file name.
     */
    private static final String FILE_EXTENSION = ".md";

    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * Creates the reader.
     *
     * @param resourcePatternResolver
     *         resolver of the application's classpath resources
     * @throws NullPointerException
     *         if {@code resourcePatternResolver} is {@code null}
     */
    ReleaseNotesReader( @Nonnull ResourcePatternResolver resourcePatternResolver ) {
        this.resourcePatternResolver = requireNonNull( resourcePatternResolver,
                "resourcePatternResolver must not be null" );
    }

    /**
     * Reads every valid change list file.
     *
     * @return unmodifiable list of change lists, newest version first
     * @throws UncheckedIOException
     *         if the classpath location cannot be listed
     */
    @Nonnull
    List< ReleaseNotes > readAll() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources( LOCATION_PATTERN );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Release notes files cannot be listed", exception );
        }

        List< ReleaseNotes > releaseNotes = new ArrayList<>();
        for ( Resource resource : resources ) {
            read( resource ).ifPresent( releaseNotes::add );
        }
        releaseNotes.sort( Comparator.comparing( ReleaseNotes::version ).reversed() );
        log.debug( "Read {} of {} release notes files", releaseNotes.size(), resources.length );
        return List.copyOf( releaseNotes );
    }

    /**
     * Extracts the version from a change list file name.
     *
     * @param fileName
     *         file name such as {@code 1.2.3.md}
     * @return version of the file, or an empty optional for {@code null} or any other name
     */
    static @Nonnull Optional< ReleaseVersion > versionOf( @Nullable String fileName ) {
        if ( fileName == null || !fileName.endsWith( FILE_EXTENSION ) ) {
            return Optional.empty();
        }
        return ReleaseVersion.parse( fileName.substring( 0, fileName.length() - FILE_EXTENSION.length() ) );
    }

    private Optional< ReleaseNotes > read( Resource resource ) {
        String fileName = resource.getFilename();
        Optional< ReleaseVersion > version = versionOf( fileName );
        if ( version.isEmpty() ) {
            log.warn( "Release notes file {} is not named <version>.md and is skipped", fileName );
            return Optional.empty();
        }

        try {
            return Optional.of( ReleaseNotesParser.parse( version.get(), resource.getContentAsString( UTF_8 ) ) );
        } catch ( IOException exception ) {
            log.warn( "Release notes file {} cannot be read and is skipped", fileName, exception );
        } catch ( IllegalArgumentException exception ) {
            log.warn( "Release notes file {} is malformed and skipped: {}", fileName, exception.getMessage() );
        }
        return Optional.empty();
    }
}
