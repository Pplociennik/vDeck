package com.github.pplociennik.vdeck.shared.log;

import com.github.pplociennik.vdeck.runtime.installation.RunLogFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PanelRunLog} creates the run log of the panel and publishes it to Log4j2. The class
 * is a static utility, so the tests call it directly and need no {@code underTest} instance.
 */
class PanelRunLogTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final LocalDateTime NOW = LocalDateTime.of( 2026, 10, 28, 21, 5, 9 );

    @TempDir
    private Path directory;

    private Properties properties;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates empty system properties for each test.
     */
    @BeforeEach
    void setUp() {
        properties = new Properties();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the file is created in the panel's day directory and its path is published.
     */
    @Test
    void shouldCreateFileAndPublishPath_whenInstallationIsWritable() throws Exception {
        // GIVEN
        Path installation = directory.toRealPath();

        // WHEN
        Optional< Path > result = PanelRunLog.configure( properties, installation, NOW );

        // THEN
        Path expected = installation.resolve( ".vDeck/logs/panel/2026-10-28/2026-10-28_21-05-09.log" );
        assertThat( result ).contains( expected );
        assertThat( expected ).isRegularFile();
        assertThat( properties.getProperty( RunLogFile.LOG_FILE_PROPERTY ) ).isEqualTo( expected.toString() );
        assertThat( properties.getProperty( PanelRunLog.OVERRIDE_PROPERTY ) ).isEqualTo( PanelRunLog.OVERRIDES );
    }

    /**
     * Verifies that the overrides of a run with a file start with exactly the console override of
     * {@code application.yaml}, which the system property replaces, followed by the file override.
     */
    @Test
    void shouldRepeatYamlOverride_whenFileOverrideIsAdded() throws Exception {
        // GIVEN
        String yaml;
        try ( var stream = getClass().getResourceAsStream( "/application.yaml" ) ) {
            yaml = new String( stream.readAllBytes(), StandardCharsets.UTF_8 ).replace( "\r\n", "\n" );
        }

        // WHEN
        String consoleOverride = PanelRunLog.OVERRIDES.substring( 0, PanelRunLog.OVERRIDES.indexOf( ',' ) );

        // THEN
        assertThat( yaml ).contains( "override: " + consoleOverride + "\n" );
        assertThat( PanelRunLog.OVERRIDES ).endsWith( "," + PanelRunLog.FILE_CONFIGURATION );
    }

    /**
     * Verifies that an installation that does not exist leaves the panel without a file log and publishes
     * nothing, so Log4j2 adds no file appender.
     */
    @Test
    void shouldPublishNothing_whenFileCannotBeCreated() throws Exception {
        // GIVEN
        Path missing = directory.toRealPath().resolve( "missing" );

        // WHEN
        Optional< Path > result = PanelRunLog.configure( properties, missing, NOW );

        // THEN
        assertThat( result ).isEmpty();
        assertThat( properties ).doesNotContainKey( RunLogFile.LOG_FILE_PROPERTY );
        assertThat( properties ).doesNotContainKey( PanelRunLog.OVERRIDE_PROPERTY );
        assertThat( Files.exists( missing ) ).isFalse();
    }

    /**
     * Verifies that missing arguments are rejected before any file is created.
     */
    @Test
    void shouldRejectNull_whenArgumentIsMissing() {
        // GIVEN
        Path installation = directory;

        // WHEN
        var missingProperties = catchThrowable( () -> PanelRunLog.configure( null, installation, NOW ) );
        var missingInstallation = catchThrowable( () -> PanelRunLog.configure( properties, null, NOW ) );
        var missingNow = catchThrowable( () -> PanelRunLog.configure( properties, installation, null ) );

        // THEN
        assertThat( missingProperties ).isInstanceOf( NullPointerException.class );
        assertThat( missingInstallation ).isInstanceOf( NullPointerException.class );
        assertThat( missingNow ).isInstanceOf( NullPointerException.class );
        assertThat( directory.resolve( ".vDeck" ) ).doesNotExist();
    }
}
