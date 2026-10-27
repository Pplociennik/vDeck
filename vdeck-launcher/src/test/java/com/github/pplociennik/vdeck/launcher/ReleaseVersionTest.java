package com.github.pplociennik.vdeck.launcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies release version parsing and the never-downgrade comparison.
 */
class ReleaseVersionTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ReleaseVersion underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates version 1.9.0.
     */
    @BeforeEach
    void setUp() {
        underTest = new ReleaseVersion( 1, 9, 0 );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an exact release version is parsed and printed unchanged.
     */
    @Test
    void shouldParseVersion_whenTextIsReleaseVersion() {
        // GIVEN
        String text = "1.10.2";

        // WHEN
        var parsed = ReleaseVersion.parse( text );

        // THEN
        assertThat( parsed ).contains( new ReleaseVersion( 1, 10, 2 ) );
        assertThat( parsed.orElseThrow() ).hasToString( text );
    }

    /**
     * Verifies that development builds and other forms are no release versions.
     *
     * @param text
     *         rejected version text
     */
    @ParameterizedTest( name = "[{index}] version={0}" )
    @NullAndEmptySource
    @ValueSource( strings = { "0.0.1-SNAPSHOT", "1.2", "1.2.3.4", "v1.2.3", "01.2.3", "1.2.3-rc1", " 1.2.3" } )
    void shouldReturnEmpty_whenTextIsNoReleaseVersion( String text ) {
        // GIVEN
        // The text is not an exact release version.

        // WHEN
        var parsed = ReleaseVersion.parse( text );

        // THEN
        assertThat( parsed ).isEmpty();
    }

    /**
     * Verifies numeric, not textual, ordering and that equal or older versions are never newer.
     */
    @Test
    void shouldCompareNumerically_whenCheckingForNewerVersion() {
        // GIVEN
        ReleaseVersion tenth = new ReleaseVersion( 1, 10, 0 );

        // WHEN
        boolean newer = tenth.isNewerThan( underTest );

        // THEN
        assertThat( newer ).isTrue();
        assertThat( underTest.isNewerThan( tenth ) ).isFalse();
        assertThat( underTest.isNewerThan( new ReleaseVersion( 1, 9, 0 ) ) ).isFalse();
        assertThat( new ReleaseVersion( 2, 0, 0 ) ).isGreaterThan( tenth );
    }

    /**
     * Verifies that negative numbers and null comparisons are rejected.
     */
    @Test
    void shouldThrowException_whenInputIsInvalid() {
        // GIVEN
        // No additional setup.

        // WHEN
        var negative = catchThrowable( () -> new ReleaseVersion( -1, 0, 0 ) );
        var missing = catchThrowable( () -> underTest.isNewerThan( null ) );

        // THEN
        assertThat( negative ).isInstanceOf( IllegalArgumentException.class );
        assertThat( missing ).isInstanceOf( NullPointerException.class );
    }
}
