package com.github.pplociennik.vdeck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.backup.service.BackupPathService;
import com.github.pplociennik.vdeck.runtime.api.ServerRuntime;
import com.github.pplociennik.vdeck.runtime.api.ServerState;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.windows.JvmConsoleControlSender;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Starts the real panel against an isolated Windows installation reached through a parent junction. */
@SpringBootTest
@EnabledOnOs( OS.WINDOWS )
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class InstallationPathIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private static Path directory;

    private static Path alias;

    private static Path installation;

    @Autowired
    private PathService underTest;

    @Autowired
    private ServerRuntime runtime;

    @Autowired
    private BackupPathService backups;

    @Autowired
    private ConsoleControlSender consoleControlSender;

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /** Removes the junction itself before JUnit deletes the isolated fixture tree. */
    @AfterAll
    static void removeAlias() throws IOException {
        if ( alias != null ) {
            Files.deleteIfExists( alias );
        }
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Spaces, Polish characters and a redirected ancestor do not prevent real Spring startup. */
    @Test
    void shouldStartPanel_whenInstallationParentIsJunction() throws IOException {
        // GIVEN
        Path expected = installation.toRealPath();
        // WHEN
        Path resolved = underTest.getBaseDirectory();
        Path backupRoot = backups.resolveRoot( resolved.resolve( "save" ) );
        // THEN
        assertThat( resolved ).isEqualTo( expected );
        assertThat( runtime.status().getState() ).isEqualTo( ServerState.STOPPED );
        assertThat( consoleControlSender ).isInstanceOf( JvmConsoleControlSender.class );
        assertThat( expected.resolve( "vdeck-runner.jar" ) ).doesNotExist();
        assertThat( underTest.getConfigurationFile( "vDeck.properties" ) ).isRegularFile();
        assertThat( backupRoot ).isEqualTo( expected.resolve( ".vDeck" ).resolve( "shutdown-snapshots" ) );
    }

    /** Redirects beneath the installation remain forbidden for recovery and world snapshots. */
    @Test
    void shouldRejectDirectory_whenJunctionIsBelowInstallation() throws Exception {
        // GIVEN
        Path outside = Files.createDirectory( directory.resolve( "outside-save" ) );
        Path link = installation.resolve( "redirected-save" );
        createJunction( link, outside );
        try {
            // WHEN
            var failure = catchThrowable( () -> FileWorldSnapshotStore.checkDirectory(
                    underTest.getBaseDirectory(), link ) );
            // THEN
            assertThat( failure ).isInstanceOf( IOException.class );
        } finally {
            Files.delete( link );
        }
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Registers a redirected installation before the real runtime, settings and database beans initialize.
     *
     * @param registry
     *         isolated Spring test property registry
     * @throws Exception
     *         if the temporary filesystem fixture cannot be prepared
     */
    @DynamicPropertySource
    static void configureInstallation( DynamicPropertyRegistry registry ) throws Exception {
        Path realParent = Files.createDirectory( directory.resolve( "real-parent" ) );
        installation = Files.createDirectory( realParent.resolve( "Valheim Serwer \u0105\u0119" ) );
        alias = directory.resolve( "parent-alias" );
        createJunction( alias, realParent );
        registry.add( "vdeck.server-directory", () -> alias.resolve( installation.getFileName() ).toString() );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + installation.resolve( "vdeck.db" ) );
    }

    private static void createJunction( Path link, Path target ) throws Exception {
        Process helper = new ProcessBuilder( "cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString() )
                .redirectErrorStream( true ).start();
        try {
            assertThat( helper.waitFor( 10, TimeUnit.SECONDS ) ).isTrue();
            assertThat( helper.exitValue() ).isZero();
        } finally {
            if ( helper.isAlive() ) {
                helper.destroyForcibly();
            }
        }
    }
}
