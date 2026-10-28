package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the validation and defensive copies of {@link ReleaseNotes}, {@link ReleaseNotesSection} and
 * {@link ReleaseNotesEntry}, the immutable model of a change list.
 */
class ReleaseNotesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final ReleaseVersion VERSION = new ReleaseVersion( 1, 0, 0 );

    private static final ReleaseNotesEntry ENTRY = new ReleaseNotesEntry( "Tytuł", "Opis." );

    private ReleaseNotes underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a change list with one section of one entry.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseNotes( VERSION,
                List.of( new ReleaseNotesSection( ReleaseNotesCategory.FEATURES, List.of( ENTRY ) ) ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that later changes to the lists passed in do not reach the model.
     */
    @Test
    void shouldCopyLists_whenCreated() {
        // GIVEN
        List< ReleaseNotesEntry > entries = new ArrayList<>( List.of( ENTRY ) );
        List< ReleaseNotesSection > sections = new ArrayList<>();
        sections.add( new ReleaseNotesSection( ReleaseNotesCategory.FIXES, entries ) );

        // WHEN
        underTest = new ReleaseNotes( VERSION, sections );
        entries.clear();
        sections.clear();

        // THEN
        assertThat( underTest.sections() ).hasSize( 1 );
        assertThat( underTest.sections().getFirst().entries() ).containsExactly( ENTRY );
    }

    /**
     * Verifies that the exposed lists cannot be modified.
     */
    @Test
    void shouldExposeUnmodifiableLists_whenRead() {
        // GIVEN
        // a change list with one section of one entry

        // WHEN
        var sectionsFailure = catchThrowable( () -> underTest.sections().clear() );
        var entriesFailure = catchThrowable( () -> underTest.sections().getFirst().entries().clear() );

        // THEN
        assertThat( sectionsFailure ).isInstanceOf( UnsupportedOperationException.class );
        assertThat( entriesFailure ).isInstanceOf( UnsupportedOperationException.class );
    }

    /**
     * Verifies that a change list requires its version and sections.
     */
    @Test
    void shouldThrowException_whenChangeListComponentIsNull() {
        // GIVEN
        // missing components

        // WHEN
        var versionFailure = catchThrowable( () -> new ReleaseNotes( null, List.of() ) );
        var sectionsFailure = catchThrowable( () -> new ReleaseNotes( VERSION, null ) );

        // THEN
        assertThat( versionFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "version" );
        assertThat( sectionsFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "sections" );
    }

    /**
     * Verifies that a section requires its category and entries.
     */
    @Test
    void shouldThrowException_whenSectionComponentIsNull() {
        // GIVEN
        // missing components

        // WHEN
        var categoryFailure = catchThrowable( () -> new ReleaseNotesSection( null, List.of() ) );
        var entriesFailure = catchThrowable( () -> new ReleaseNotesSection( ReleaseNotesCategory.CHANGES, null ) );

        // THEN
        assertThat( categoryFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "category" );
        assertThat( entriesFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "entries" );
    }

    /**
     * Verifies that an entry requires its title and description.
     */
    @Test
    void shouldThrowException_whenEntryTextIsNull() {
        // GIVEN
        // missing texts

        // WHEN
        var titleFailure = catchThrowable( () -> new ReleaseNotesEntry( null, "Opis." ) );
        var descriptionFailure = catchThrowable( () -> new ReleaseNotesEntry( "Tytuł", null ) );

        // THEN
        assertThat( titleFailure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "title" );
        assertThat( descriptionFailure ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "description" );
    }
}
