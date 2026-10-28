package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import jakarta.annotation.Nonnull;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Changes of one category within a version's change list, in file order.
 *
 * @param category
 *         kind of change the section groups
 * @param entries
 *         changes of the section; an unmodifiable copy
 */
public record ReleaseNotesSection( @Nonnull ReleaseNotesCategory category, @Nonnull List< ReleaseNotesEntry > entries ) {

    /**
     * Validates the components and copies the entries.
     *
     * @throws NullPointerException
     *         if a component or an entry is {@code null}
     */
    public ReleaseNotesSection {
        requireNonNull( category, "category must not be null" );
        entries = List.copyOf( requireNonNull( entries, "entries must not be null" ) );
    }
}
