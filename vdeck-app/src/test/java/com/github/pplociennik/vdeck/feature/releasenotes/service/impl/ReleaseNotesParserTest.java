package com.github.pplociennik.vdeck.feature.releasenotes.service.impl;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesCategory;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesEntry;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesSection;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link ReleaseNotesParser} reads the change list format and rejects every deviation with
 * the number of the offending line.
 */
class ReleaseNotesParserTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion VERSION = new ReleaseVersion( 1, 2, 3 );

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that sections and their entries are read in file order, with titles and descriptions
     * stripped of the list and indentation markup.
     */
    @Test
    void shouldReadSectionsAndEntries_whenFileIsValid() {
        // GIVEN
        String content = """
                ## Nowości
                - **Lista zmian**
                  Panel pokazuje zmiany po aktualizacji.
                - **Restart serwera**
                  Nowy przycisk na wyspie.

                ## Poprawki
                - **Konsola**
                  Ostatnia linia pojawia się od razu.
                """;

        // WHEN
        ReleaseNotes result = ReleaseNotesParser.parse( VERSION, content );

        // THEN
        assertThat( result.version() ).isEqualTo( VERSION );
        assertThat( result.sections() ).containsExactly(
                new ReleaseNotesSection( ReleaseNotesCategory.FEATURES, List.of(
                        new ReleaseNotesEntry( "Lista zmian", "Panel pokazuje zmiany po aktualizacji." ),
                        new ReleaseNotesEntry( "Restart serwera", "Nowy przycisk na wyspie." ) ) ),
                new ReleaseNotesSection( ReleaseNotesCategory.FIXES, List.of(
                        new ReleaseNotesEntry( "Konsola", "Ostatnia linia pojawia się od razu." ) ) ) );
    }

    /**
     * Verifies that a new version's file holding only the headings is valid and yields no sections.
     */
    @Test
    void shouldReturnNoSections_whenFileHoldsOnlyHeadings() {
        // GIVEN
        String content = "## Nowości\n\n## Zmiany\n\n## Poprawki\n";

        // WHEN
        ReleaseNotes result = ReleaseNotesParser.parse( VERSION, content );

        // THEN
        assertThat( result.sections() ).isEmpty();
    }

    /**
     * Verifies that an empty file is valid and yields no sections.
     */
    @Test
    void shouldReturnNoSections_whenFileIsEmpty() {
        // GIVEN
        String content = "";

        // WHEN
        ReleaseNotes result = ReleaseNotesParser.parse( VERSION, content );

        // THEN
        assertThat( result.sections() ).isEmpty();
    }

    /**
     * Verifies that a byte order mark, Windows line endings and trailing spaces written by an editor do
     * not break the format.
     */
    @Test
    void shouldIgnoreEditorArtifacts_whenFileHasByteOrderMarkAndTrailingSpaces() {
        // GIVEN
        String content = "﻿## Zmiany  \r\n- **Baza danych** \r\n  Aktualizowana przy starcie.  \r\n";

        // WHEN
        ReleaseNotes result = ReleaseNotesParser.parse( VERSION, content );

        // THEN
        assertThat( result.sections() ).containsExactly( new ReleaseNotesSection( ReleaseNotesCategory.CHANGES,
                List.of( new ReleaseNotesEntry( "Baza danych", "Aktualizowana przy starcie." ) ) ) );
    }

    /**
     * Verifies that a title and a description exactly at their length limits are accepted.
     */
    @Test
    void shouldAcceptTexts_whenLengthsAreAtLimits() {
        // GIVEN
        String title = "t".repeat( ReleaseNotesParser.MAX_TITLE_LENGTH );
        String description = "d".repeat( ReleaseNotesParser.MAX_DESCRIPTION_LENGTH );
        String content = "## Nowości\n- **" + title + "**\n  " + description + "\n";

        // WHEN
        ReleaseNotes result = ReleaseNotesParser.parse( VERSION, content );

        // THEN
        assertThat( result.sections().getFirst().entries() )
                .containsExactly( new ReleaseNotesEntry( title, description ) );
    }

    /**
     * Verifies that every deviation from the format is rejected with the number of the line that broke it.
     *
     * @param content
     *         file content with escaped line breaks
     * @param expectedMessage
     *         start of the expected error message
     */
    @ParameterizedTest( name = "[{index}] {1}" )
    @CsvSource( delimiter = '|', quoteCharacter = '"', value = {
            "## Inne\\n                                         | Line 1: unknown heading 'Inne'",
            "## Poprawki\\n## Nowości\\n                         | Line 2: heading 'Nowości' is repeated or out of order",
            "## Zmiany\\n## Zmiany\\n                            | Line 2: heading 'Zmiany' is repeated or out of order",
            "- **Tytuł**\\n  Opis.\\n                            | Line 1: entry before the first heading",
            "## Nowości\\n- **Tytuł**\\n                         | Line 2: entry 'Tytuł' has no description line",
            "## Nowości\\n- **A**\\n- **B**\\n  Opis.\\n          | Line 3: entry 'A' has no description line",
            "## Nowości\\n- **A**\\n## Zmiany\\n                 | Line 3: entry 'A' has no description line",
            "## Nowości\\n  Opis bez tytułu.\\n                  | Line 2: description without an entry title",
            "## Nowości\\n- **A**\\n  Opis.\\n  Druga linia.\\n  | Line 4: description without an entry title",
            "## Nowości\\n- Tytuł bez pogrubienia\\n           | Line 2: entry title must be written as '- **Title**'",
            "## Nowości\\n- **  **\\n  Opis.\\n                  | Line 2: empty title",
            "## Nowości\\nZwykły tekst\\n                       | Line 2: expected a '## ' heading"
    } )
    void shouldThrowException_whenFormatIsBroken( String content, String expectedMessage ) {
        // GIVEN
        String text = content.strip().replace( "\\n", "\n" );

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesParser.parse( VERSION, text ) );

        // THEN
        assertThat( exception ).isInstanceOf( IllegalArgumentException.class ).hasMessageStartingWith( expectedMessage );
    }

    /**
     * Verifies that a title one character longer than the limit is rejected.
     */
    @Test
    void shouldThrowException_whenTitleIsTooLong() {
        // GIVEN
        String content = "## Nowości\n- **" + "t".repeat( ReleaseNotesParser.MAX_TITLE_LENGTH + 1 ) + "**\n  Opis.\n";

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesParser.parse( VERSION, content ) );

        // THEN
        assertThat( exception ).isInstanceOf( IllegalArgumentException.class )
                .hasMessage( "Line 2: title longer than 80 characters" );
    }

    /**
     * Verifies that a description one character longer than the limit is rejected.
     */
    @Test
    void shouldThrowException_whenDescriptionIsTooLong() {
        // GIVEN
        String content = "## Nowości\n- **A**\n  " + "d".repeat( ReleaseNotesParser.MAX_DESCRIPTION_LENGTH + 1 ) + "\n";

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesParser.parse( VERSION, content ) );

        // THEN
        assertThat( exception ).isInstanceOf( IllegalArgumentException.class )
                .hasMessage( "Line 3: description longer than 200 characters" );
    }

    /**
     * Verifies that a missing version is rejected.
     */
    @Test
    void shouldThrowException_whenVersionIsNull() {
        // GIVEN
        // no version

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesParser.parse( null, "" ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "version" );
    }

    /**
     * Verifies that missing content is rejected.
     */
    @Test
    void shouldThrowException_whenContentIsNull() {
        // GIVEN
        // no content

        // WHEN
        var exception = catchThrowable( () -> ReleaseNotesParser.parse( VERSION, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "content" );
    }
}
