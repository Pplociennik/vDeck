package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the file actions of a transaction.
 */
class TransactionFilesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private TransactionFiles underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the helper for a canonical installation.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        underTest = new TransactionFiles( layout );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the installed, source and backup locations of the different item kinds.
     */
    @Test
    void shouldResolveLocations_whenAskedForItems() {
        // GIVEN
        DistributionItem settings = DistributionItem.PANEL_SETTINGS;

        // WHEN
        Path source = underTest.source( settings );
        Path stagedLib = underTest.source( DistributionItem.LIB );

        // THEN
        assertThat( source ).isEqualTo( layout.mergedDirectory().resolve( settings.getPath() ) );
        assertThat( stagedLib ).isEqualTo( layout.stagedDirectory().resolve( "lib" ) );
        assertThat( underTest.backup( DistributionItem.JRE ) ).isEqualTo( layout.backupDirectory().resolve( "jre" ) );
        assertThat( underTest.installed( DistributionItem.MANUAL ) ).isEqualTo( layout.resolve( "vDeck-instrukcja.pdf" ) );
    }

    /**
     * Verifies that a directory is moved into a parent that is created on demand.
     */
    @Test
    void shouldMoveDirectory_whenTargetParentIsMissing() throws Exception {
        // GIVEN
        Path lib = Files.createDirectory( layout.resolve( "lib" ) );
        Files.writeString( lib.resolve( "a.jar" ), "jar" );

        // WHEN
        underTest.move( lib, underTest.backup( DistributionItem.LIB ), false );

        // THEN
        assertThat( lib ).doesNotExist();
        assertThat( underTest.backup( DistributionItem.LIB ).resolve( "a.jar" ) ).hasContent( "jar" );
    }

    /**
     * Verifies that a move without replacement never overwrites an existing target.
     */
    @Test
    void shouldThrowException_whenMoveTargetExistsWithoutReplacement() throws Exception {
        // GIVEN
        Path source = Files.writeString( layout.resolve( "a.txt" ), "a" );
        Path target = Files.writeString( layout.resolve( "b.txt" ), "b" );

        // WHEN
        var failure = catchThrowable( () -> underTest.move( source, target, false ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( target ).hasContent( "b" );
    }

    /**
     * Verifies that a durable copy replaces the target and returns its digest.
     */
    @Test
    void shouldReplaceTarget_whenCopyingOver() throws Exception {
        // GIVEN
        Path source = Files.writeString( layout.resolve( "source.txt" ), "hello" );
        Path target = Files.writeString( layout.resolve( "target.txt" ), "old" );

        // WHEN
        String digest = underTest.copyOver( source, target );

        // THEN
        assertThat( target ).hasContent( "hello" );
        assertThat( digest ).isEqualTo( underTest.digestOrNull( target ) );
        try ( var files = Files.list( layout.installation() ) ) {
            assertThat( files ).hasSize( 2 );
        }
    }

    /**
     * Verifies that a missing file has no digest and a relative path is used in logs.
     */
    @Test
    void shouldReturnNullDigest_whenFileIsMissing() throws Exception {
        // GIVEN
        Path missing = layout.resolve( "missing.txt" );

        // WHEN
        String digest = underTest.digestOrNull( missing );

        // THEN
        assertThat( digest ).isNull();
        assertThat( underTest.exists( missing ) ).isFalse();
        assertThat( underTest.relative( missing ) ).isEqualTo( Path.of( "missing.txt" ) );
    }

    /**
     * Verifies that the layout is required.
     */
    @Test
    void shouldThrowException_whenLayoutIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> new TransactionFiles( null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
