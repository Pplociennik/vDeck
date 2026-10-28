package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Kind of change a section of a version's change list groups. Constants are declared in the order the
 * sections must appear in a {@code release-notes/<version>.md} file.
 *
 * <p>Each constant knows the exact second-level heading that opens its section in the file and the
 * translation key the panel shows instead. The file heading is a fixed part of the file format, read by
 * people on the GitHub release page as well, so it is not an interface text.
 */
public enum ReleaseNotesCategory implements TranslationKeyIf {

    /**
     * New features.
     */
    FEATURES( "Nowości", "releaseNotes.category.features" ),

    /**
     * Changed behaviour of existing features.
     */
    CHANGES( "Zmiany", "releaseNotes.category.changes" ),

    /**
     * Corrected defects.
     */
    FIXES( "Poprawki", "releaseNotes.category.fixes" );

    private final String heading;

    private final String key;

    ReleaseNotesCategory( @Nonnull String heading, @Nonnull String key ) {
        this.heading = requireNonNull( heading, "heading must not be null" );
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Finds the category whose section the given file heading opens.
     *
     * @param heading
     *         heading text without the leading {@code ## }, compared exactly
     * @return matching category, or an empty optional for {@code null} or any other heading
     */
    public static @Nonnull Optional< ReleaseNotesCategory > fromHeading( @Nullable String heading ) {
        return Arrays.stream( values() )
                .filter( category -> category.heading.equals( heading ) )
                .findFirst();
    }

    /**
     * Returns the translation key of the section title shown in the panel.
     *
     * @return key resolved against the translation files
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
