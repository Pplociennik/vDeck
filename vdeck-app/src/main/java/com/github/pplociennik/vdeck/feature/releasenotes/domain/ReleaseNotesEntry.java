package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * One change of a version's change list: a one-line title and a very short description below it, both
 * written for the user of the panel. Length and format rules belong to the file parser.
 *
 * @param title
 *         one-line name of the change
 * @param description
 *         one or two sentences explaining the change
 */
public record ReleaseNotesEntry( @Nonnull String title, @Nonnull String description ) {

    /**
     * Validates that both texts are present.
     *
     * @throws NullPointerException
     *         if a text is {@code null}
     */
    public ReleaseNotesEntry {
        requireNonNull( title, "title must not be null" );
        requireNonNull( description, "description must not be null" );
    }
}
