package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import jakarta.annotation.Nonnull;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Change list of one vDeck version, read from {@code release-notes/<version>.md} in the application. The
 * same file is the description of the version's GitHub release.
 *
 * @param version
 *         version the changes were released in
 * @param sections
 *         sections that contain at least one change, in category order; an unmodifiable copy
 */
public record ReleaseNotes( @Nonnull ReleaseVersion version, @Nonnull List< ReleaseNotesSection > sections ) {

    /**
     * Validates the components and copies the sections.
     *
     * @throws NullPointerException
     *         if a component or a section is {@code null}
     */
    public ReleaseNotes {
        requireNonNull( version, "version must not be null" );
        sections = List.copyOf( requireNonNull( sections, "sections must not be null" ) );
    }
}
