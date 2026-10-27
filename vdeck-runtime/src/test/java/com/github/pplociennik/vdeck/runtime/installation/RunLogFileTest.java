package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the per-run log files of the launcher and updater.
 */
class RunLogFileTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDateTime START = LocalDateTime.of( 2026, 10, 27, 9, 5, 7 );

    @TempDir
    private Path directory;

    private Path installation;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a canonical installation directory.
     */
    @BeforeEach
    void setUp() throws Exception {
        installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies the component and day directories and the timestamped file name.
     */
    @Test
    void shouldCreateDatedFile_whenRunStarts() {
        // GIVEN
        Path expected = installation.resolve( ".vDeck/logs/launcher/2026-10-27/2026-10-27_09-05-07.log" );

        // WHEN
        Optional< Path > file = RunLogFile.create( installation, "launcher", START );

        // THEN
        assertThat( file ).contains( expected );
        assertThat( expected ).isEmptyFile();
    }

    /**
     * Verifies that runs started in the same second get numbered files instead of sharing one.
     */
    @Test
    void shouldAddSuffix_whenFileOfSameSecondExists() {
        // GIVEN
        RunLogFile.create( installation, "updater", START );

        // WHEN
        Optional< Path > second = RunLogFile.create( installation, "updater", START );
        Optional< Path > third = RunLogFile.create( installation, "updater", START );

        // THEN
        assertThat( second.orElseThrow().getFileName() ).hasToString( "2026-10-27_09-05-07-1.log" );
        assertThat( third.orElseThrow().getFileName() ).hasToString( "2026-10-27_09-05-07-2.log" );
    }

    /**
     * Verifies that a missing installation yields no file instead of an exception.
     */
    @Test
    void shouldReturnEmpty_whenInstallationIsMissing() {
        // GIVEN
        Path missing = installation.resolve( "missing" );

        // WHEN
        Optional< Path > file = RunLogFile.create( missing, "launcher", START );

        // THEN
        assertThat( file ).isEmpty();
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> RunLogFile.create( installation, null, START ) );

        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }
}
