package com.github.pplociennik.vdeck.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

/** Verifies world selection cannot redirect a snapshot through a filename or an overlapping root. */
class WorldContextTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private WorldContext underTest;

    private Path save;

    private Path backups;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates normalized, distinct roots without filesystem access. */
    @BeforeEach
    void setUp() {
        save = Path.of( "installation/save" ).toAbsolutePath();
        backups = Path.of( "backup-root" ).toAbsolutePath();
        underTest = new WorldContext( "Dedicated", save, backups );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** Checks captured directories and Unicode world names remain unchanged. */
    @Test
    void shouldPreserveSelection_whenContextIsValid() {
        // GIVEN
        String name = "Świat z nazwą";
        // WHEN
        underTest = new WorldContext( name, save.resolve( "x/.." ), backups );
        // THEN
        assertThat( underTest.name() ).isEqualTo( name );
        assertThat( underTest.saveDirectory() ).isEqualTo( save );
    }

    /**
     * Rejects names that could escape the intended world set.
     *
     * @param name
     *         unsafe file stem
     */
    @ParameterizedTest( name = "[{index}] unsafe stem {0}" )
    @ValueSource( strings = { "", " ", "..", ".", "../world", "x\\world", "x:y", "x.", "x ", "CON", "nul.old", "COM1" } )
    void shouldRejectName_whenStemIsUnsafe( String name ) {
        // GIVEN
        // An untrusted file stem.
        // WHEN
        var failure = catchThrowable( () -> new WorldContext( name, save, backups ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Rejects roots which could copy a world into itself. */
    @Test
    void shouldRejectRoots_whenTheyOverlap() {
        // GIVEN
        Path target = save.resolve( "backups" );
        // WHEN
        var failure = catchThrowable( () -> new WorldContext( "Dedicated", save, target ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Rejects each missing component explicitly.
     *
     * @param component
     *         omitted component
     */
    @ParameterizedTest( name = "[{index}] missing {0}" )
    @ValueSource( strings = { "name", "saveDirectory", "snapshotDirectory" } )
    void shouldRejectNull_whenComponentIsMissing( String component ) {
        // GIVEN
        // One omitted component.
        // WHEN
        var failure = catchThrowable( () -> new WorldContext( component.equals( "name" ) ? null : "Dedicated",
                component.equals( "saveDirectory" ) ? null : save,
                component.equals( "snapshotDirectory" ) ? null : backups ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( component );
    }

    /** Rejects relative directories before any file can be copied. */
    @Test
    void shouldRejectDirectory_whenItIsRelative() {
        // GIVEN
        Path relative = Path.of( "save" );
        // WHEN
        var failure = catchThrowable( () -> new WorldContext( "Dedicated", relative, backups ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }
}
