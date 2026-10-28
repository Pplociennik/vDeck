package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ReleaseNotesCategory} recognises exactly the file headings of its sections and
 * keeps the order in which the sections must appear.
 */
class ReleaseNotesCategoryTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that each section heading of the file format resolves to its category.
     *
     * @param heading
     *         heading text without the leading {@code ## }
     * @param expected
     *         category the heading opens
     */
    @ParameterizedTest( name = "[{index}] heading={0}" )
    @CsvSource( { "Nowości, FEATURES", "Zmiany, CHANGES", "Poprawki, FIXES" } )
    void shouldReturnCategory_whenHeadingIsKnown( String heading, ReleaseNotesCategory expected ) {
        // GIVEN
        // a heading of the change list format

        // WHEN
        var result = ReleaseNotesCategory.fromHeading( heading );

        // THEN
        assertThat( result ).contains( expected );
    }

    /**
     * Verifies that any other heading, including a differently written known one, opens no section.
     *
     * @param heading
     *         unknown heading text
     */
    @ParameterizedTest( name = "[{index}] heading={0}" )
    @NullAndEmptySource
    @ValueSource( strings = { "Inne", "nowości", "Nowosci", " Zmiany", "FEATURES" } )
    void shouldReturnEmpty_whenHeadingIsUnknown( String heading ) {
        // GIVEN
        // a heading outside the change list format

        // WHEN
        var result = ReleaseNotesCategory.fromHeading( heading );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that the declaration order is the order the parser enforces for the sections.
     */
    @Test
    void shouldOrderSections_whenCategoriesAreCompared() {
        // GIVEN
        // all categories

        // WHEN
        var result = ReleaseNotesCategory.values();

        // THEN
        assertThat( result ).containsExactly( ReleaseNotesCategory.FEATURES, ReleaseNotesCategory.CHANGES,
                ReleaseNotesCategory.FIXES );
        assertThat( ReleaseNotesCategory.FIXES.getKey() ).isEqualTo( "releaseNotes.category.fixes" );
    }
}
