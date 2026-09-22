package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link SystemPropertyValues} collects the values of settings in insertion order, rejects
 * incomplete or duplicate entries, and never exposes its entries for modification.
 */
class SystemPropertyValuesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private SystemPropertyValues underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh, empty set of values before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = SystemPropertyValues.create();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a newly created set holds no entry.
     */
    @Test
    void shouldHoldNoEntry_whenCreated() {
        // GIVEN
        // the set created in setUp

        // WHEN
        Map< SystemPropertyIf, String > result = underTest.asMap();

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that entries added in a chain are kept in the order they were added, including an empty
     * value.
     */
    @Test
    void shouldKeepInsertionOrder_whenEntriesAreAdded() {
        // GIVEN
        underTest.with( TestProperty.SECOND, "second" ).with( TestProperty.FIRST, "" );

        // WHEN
        Map< SystemPropertyIf, String > result = underTest.asMap();

        // THEN
        assertThat( result ).containsExactly(
                Map.entry( TestProperty.SECOND, "second" ),
                Map.entry( TestProperty.FIRST, "" ) );
    }

    /**
     * Verifies that adding an entry returns the same set, so entries can be chained.
     */
    @Test
    void shouldReturnSameSet_whenEntryIsAdded() {
        // GIVEN
        // the set created in setUp

        // WHEN
        SystemPropertyValues result = underTest.with( TestProperty.FIRST, "first" );

        // THEN
        assertThat( result ).isSameAs( underTest );
    }

    /**
     * Verifies that a second value for the same setting is rejected instead of silently replacing the
     * first one.
     */
    @Test
    void shouldThrowException_whenSettingIsAddedTwice() {
        // GIVEN
        underTest.with( TestProperty.FIRST, "first" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.with( TestProperty.FIRST, "other" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "already has a value" );
        assertThat( underTest.asMap() ).containsExactly( Map.entry( TestProperty.FIRST, "first" ) );
    }

    /**
     * Verifies that the map view cannot be used to change the entries.
     */
    @Test
    void shouldRejectModification_whenMapViewIsChanged() {
        // GIVEN
        Map< SystemPropertyIf, String > view = underTest.with( TestProperty.FIRST, "first" ).asMap();

        // WHEN
        Throwable thrown = catchThrowable( () -> view.put( TestProperty.SECOND, "second" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UnsupportedOperationException.class );
    }

    /**
     * Verifies that two sets with the same entries are equal, which is what callers compare in tests of
     * the properties service.
     */
    @Test
    void shouldBeEqual_whenEntriesAreEqual() {
        // GIVEN
        underTest.with( TestProperty.FIRST, "first" );
        SystemPropertyValues other = SystemPropertyValues.create().with( TestProperty.FIRST, "first" );

        // WHEN
        boolean result = underTest.equals( other );

        // THEN
        assertThat( result ).isTrue();
        assertThat( underTest ).hasSameHashCodeAs( other );
    }

    /**
     * Verifies that the textual form never reveals a value, because a value may be a secret.
     */
    @Test
    void shouldNotRevealValue_whenConvertedToString() {
        // GIVEN
        underTest.with( TestProperty.FIRST, "Secret1" );

        // WHEN
        String result = underTest.toString();

        // THEN
        assertThat( result ).doesNotContain( "Secret1" );
    }

    /**
     * Verifies that a null setting is rejected before anything is added.
     */
    @Test
    void shouldThrowException_whenPropertyIsNull() {
        // GIVEN
        // no setting at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.with( null, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "property must not be null" );
        assertThat( underTest.asMap() ).isEmpty();
    }

    /**
     * Verifies that a null value is rejected before anything is added.
     */
    @Test
    void shouldThrowException_whenValueIsNull() {
        // GIVEN
        // no value at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.with( TestProperty.FIRST, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "value must not be null" );
        assertThat( underTest.asMap() ).isEmpty();
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Test-local settings keeping the verified contract independent of the settings vDeck declares.
     */
    private enum TestProperty implements SystemPropertyIf {

        FIRST,
        SECOND;

        @Override
        public @Nonnull String getName() {
            return name().toLowerCase();
        }

        @Override
        public boolean isMandatory() {
            return false;
        }
    }
}
