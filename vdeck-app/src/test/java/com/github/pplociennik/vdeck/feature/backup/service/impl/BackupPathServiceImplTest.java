package com.github.pplociennik.vdeck.feature.backup.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies that snapshots resolve to the configured directory directly below {@code .vDeck}. */
class BackupPathServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    @TempDir
    private Path directory;

    private BackupPathServiceImpl underTest;

    private PathService paths;

    private SystemPropertiesService systemPropertiesService;

    private Path installation;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates a fresh isolated fixture. */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.resolve( "Valheim" ) );
        paths = mock( PathService.class );
        systemPropertiesService = mock( SystemPropertiesService.class );
        given( paths.getBaseDirectory() ).willReturn( installation );
        underTest = new BackupPathServiceImpl( paths, systemPropertiesService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Resolves the configured directory below .vDeck without creating it. */
    @Test
    void shouldResolveConfiguredRoot_whenNameIsValid() {
        // GIVEN
        givenConfiguredName( "kopie" );
        // WHEN
        var result = underTest.resolveRoot( installation.resolve( "save" ) );
        // THEN
        assertThat( result ).isEqualTo( installation.resolve( ".vDeck/kopie" ) );
        assertThat( result ).doesNotExist();
    }

    /** A configured value that is not one directory directly below .vDeck is refused. */
    @ParameterizedTest
    @ValueSource( strings = { "a/b", "..", "." } )
    void shouldRejectRoot_whenNameIsNotSingleDirectory( String name ) {
        // GIVEN
        givenConfiguredName( name );
        // WHEN
        var failure = catchThrowable( () -> underTest.resolveRoot( installation.resolve( "save" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class );
    }

    /** Rejects overlap with active data instead of recursively backing up backups. */
    @Test
    void shouldRejectRoot_whenSaveDirectoryOverlaps() {
        // GIVEN
        givenConfiguredName( "backups" );
        // WHEN
        var failure = catchThrowable( () -> underTest.resolveRoot( installation.resolve( ".vDeck" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class );
    }

    /** Rejects a regular file occupying the backup directory chain. */
    @Test
    void shouldRejectRoot_whenParentIsNotDirectory() throws Exception {
        // GIVEN
        givenConfiguredName( "backups" );
        Files.writeString( installation.resolve( ".vDeck" ), "file" );
        // WHEN
        var failure = catchThrowable( () -> underTest.resolveRoot( installation.resolve( "save" ) ) );
        // THEN
        assertThat( failure ).isInstanceOf( UncheckedIOException.class );
    }

    /** Null save directories are rejected before reading settings. */
    @Test
    void shouldRejectNull_whenSaveDirectoryIsMissing() {
        // GIVEN
        // No additional setup.
        // WHEN
        var failure = catchThrowable( () -> underTest.resolveRoot( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    /** Both collaborators are required constructor boundaries. */
    @Test
    void shouldRejectNull_whenCollaboratorIsMissing() {
        // GIVEN
        // No additional setup.
        // WHEN
        var pathsFailure = catchThrowable( () -> new BackupPathServiceImpl( null, systemPropertiesService ) );
        var propertiesFailure = catchThrowable( () -> new BackupPathServiceImpl( paths, null ) );
        // THEN
        assertThat( pathsFailure ).isInstanceOf( NullPointerException.class );
        assertThat( propertiesFailure ).isInstanceOf( NullPointerException.class );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Stores the backup directory name and lets the path mock resolve it like PathService does.
     *
     * @param name
     *         configured value of {@code snapshot.directory}
     */
    private void givenConfiguredName( String name ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.SNAPSHOT_DIRECTORY ) )
                .willReturn( name );
        String relative = ".vDeck/" + name;
        given( paths.getAbsolutePath( relative ) ).willReturn( installation.resolve( relative ).normalize() );
    }
}
