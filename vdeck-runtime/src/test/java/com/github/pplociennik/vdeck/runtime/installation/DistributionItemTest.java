package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the closed list of distribution items.
 */
class DistributionItemTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path root;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Resolves the canonical package root.
     */
    @BeforeEach
    void setUp() throws Exception {
        root = root.toRealPath();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that only exact paths of the closed list are found.
     */
    @Test
    void shouldFindItem_whenPathMatchesExactly() {
        // GIVEN
        String settings = ".vDeck/config/vDeck-start.properties";

        // WHEN
        var found = DistributionItem.ofPath( settings );
        var foreign = DistributionItem.ofPath( "vdeck.db" );
        var differentCase = DistributionItem.ofPath( "LIB" );

        // THEN
        assertThat( found ).contains( DistributionItem.START_SETTINGS );
        assertThat( foreign ).isEmpty();
        assertThat( differentCase ).isEmpty();
    }

    /**
     * Verifies that a complete root has no missing files.
     */
    @Test
    void shouldReportNothingMissing_whenRootIsComplete() throws Exception {
        // GIVEN
        for ( String file : DistributionItem.REQUIRED_FILES ) {
            Files.createDirectories( root.resolve( file ).getParent() );
            Files.writeString( root.resolve( file ), "x" );
        }

        // WHEN
        var missing = DistributionItem.missingFiles( root );

        // THEN
        assertThat( missing ).isEmpty();
    }

    /**
     * Verifies that an empty root misses every required file in list order.
     */
    @Test
    void shouldReportAllFiles_whenRootIsEmpty() {
        // GIVEN
        // The root is empty.

        // WHEN
        var missing = DistributionItem.missingFiles( root );

        // THEN
        assertThat( missing ).containsExactlyElementsOf( DistributionItem.REQUIRED_FILES );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingPath = catchThrowable( () -> DistributionItem.ofPath( null ) );
        var missingRoot = catchThrowable( () -> DistributionItem.missingFiles( null ) );

        // THEN
        assertThat( missingPath ).isInstanceOf( NullPointerException.class );
        assertThat( missingRoot ).isInstanceOf( NullPointerException.class );
    }
}
