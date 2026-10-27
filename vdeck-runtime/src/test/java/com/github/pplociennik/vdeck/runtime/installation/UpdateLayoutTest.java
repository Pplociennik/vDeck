package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the fixed locations of the recovery protocol.
 */
class UpdateLayoutTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path installation;

    private UpdateLayout underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the layout of a temporary installation.
     */
    @BeforeEach
    void setUp() {
        underTest = new UpdateLayout( installation );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the paths the start script relies on without parsing anything.
     */
    @Test
    void shouldResolveRecoveryPaths_whenAskedForThem() {
        // GIVEN
        Path update = installation.resolve( ".vDeck" ).resolve( "update" );

        // WHEN
        Path journal = underTest.journal();
        Path java = underTest.executorJava();
        Path updater = underTest.executorUpdaterJar();

        // THEN
        assertThat( journal ).isEqualTo( update.resolve( "apply.journal" ) );
        assertThat( java ).isEqualTo( update.resolve( "executor/jre/bin/java.exe" ) );
        assertThat( updater ).isEqualTo( update.resolve( "executor/vdeck-updater.jar" ) );
    }

    /**
     * Verifies the work directories below the update directory and the installed runtime.
     */
    @Test
    void shouldResolveWorkDirectories_whenAskedForThem() {
        // GIVEN
        Path update = underTest.updateDirectory();

        // WHEN
        var directories = new Path[] { underTest.downloadDirectory(), underTest.stagedDirectory(),
                underTest.mergedDirectory(), underTest.backupDirectory() };

        // THEN
        assertThat( directories ).allSatisfy( directory -> assertThat( directory.getParent() ).isEqualTo( update ) );
        assertThat( underTest.handoffDirectory() ).isEqualTo( installation.resolve( ".vDeck/handoff" ) );
        assertThat( underTest.installedJava() ).isEqualTo( installation.resolve( "jre/bin/java.exe" ) );
        assertThat( underTest.resolve( "lib/vdeck.jar" ) ).isEqualTo( installation.resolve( "lib/vdeck.jar" ) );
    }

    /**
     * Verifies that a relative or missing root is rejected.
     */
    @Test
    void shouldThrowException_whenRootIsInvalid() {
        // GIVEN
        Path relative = Path.of( "relative" );

        // WHEN
        var relativeFailure = catchThrowable( () -> new UpdateLayout( relative ) );
        var missingFailure = catchThrowable( () -> new UpdateLayout( null ) );

        // THEN
        assertThat( relativeFailure ).isInstanceOf( IllegalArgumentException.class );
        assertThat( missingFailure ).isInstanceOf( NullPointerException.class );
    }
}
