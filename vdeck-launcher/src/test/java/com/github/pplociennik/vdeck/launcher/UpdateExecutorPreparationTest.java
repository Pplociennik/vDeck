package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the preparation of the independent executor runtime.
 */
class UpdateExecutorPreparationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private UpdateExecutorPreparation underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation with a staged package.
     */
    @BeforeEach
    void setUp() throws Exception {
        layout = new UpdateLayout( Files.createDirectory( directory.toRealPath().resolve( "installation" ) ) );
        PackageFixture.writeTree( layout.stagedDirectory(), "1.1.0" );
        underTest = new UpdateExecutorPreparation();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the executor holds independent, identical copies of the runtime and the updater, and
     * that the staged package stays complete.
     */
    @Test
    void shouldCopyRuntimeAndUpdater_whenStagedPackageIsPublished() throws Exception {
        // GIVEN
        Path stagedJava = layout.stagedDirectory().resolve( "jre/bin/java.exe" );

        // WHEN
        underTest.prepare( layout, Deadline.after( Duration.ofMinutes( 1 ) ) );

        // THEN
        assertThat( DurableFiles.sha256( layout.executorJava() ) ).isEqualTo( DurableFiles.sha256( stagedJava ) );
        assertThat( layout.executorUpdaterJar() ).exists();
        assertThat( layout.executorDirectory().resolve( "jre/lib/modules" ) ).hasContent( "modules 1.1.0" );
        Files.writeString( layout.executorJava(), "changed copy" );
        assertThat( stagedJava ).hasContent( "jre/bin/java.exe 1.1.0" );
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).containsExactlyInAnyOrder( layout.stagedDirectory(), layout.executorDirectory() );
        }
    }

    /**
     * Verifies that a missing updater publishes no executor and leaves no temporary directory.
     */
    @Test
    void shouldPublishNothing_whenUpdaterIsMissing() throws Exception {
        // GIVEN
        Files.delete( layout.stagedDirectory().resolve( UpdateLayout.UPDATER_JAR ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( layout, Deadline.after( Duration.ofMinutes( 1 ) ) ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        try ( var files = Files.list( layout.updateDirectory() ) ) {
            assertThat( files ).containsExactly( layout.stagedDirectory() );
        }
    }

    /**
     * Verifies that an expired deadline publishes no executor.
     */
    @Test
    void shouldPublishNothing_whenDeadlineHasExpired() {
        // GIVEN
        Deadline expired = Deadline.after( Duration.ZERO, () -> 0L );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( layout, expired ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( layout.executorDirectory() ).doesNotExist();
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare( layout, null ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
