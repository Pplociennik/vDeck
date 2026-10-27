package com.github.pplociennik.vdeck.launcher;

import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.RunLogFile;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap of the launcher JAR started by {@code start-vDeck.bat}. It deliberately has no logger: the
 * run log file must be chosen and published as a system property before the first logger initializes
 * Log4j2 (AD-024).
 */
public final class LauncherMain {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds( 5 );

    private LauncherMain() {
    }

    /**
     * Creates this run's log file below {@code .vDeck/logs/launcher} and starts vDeck. Exits with the
     * panel's exit code, 0 after handing over to the updater, 1 on failure and 2 for an invalid invocation.
     *
     * @param arguments
     *         {@code [--after-update] [--handoff <id>] [-- <panel arguments>]}
     * @throws NullPointerException
     *         if the arguments are null
     */
    public static void main( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        String directory = System.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        Path installation = directory == null ? null : resolve( directory );
        if ( installation != null ) {
            RunLogFile.create( installation, "launcher", LocalDateTime.now() ).ifPresent(
                    file -> System.setProperty( RunLogFile.LOG_FILE_PROPERTY, file.toString() ) );
        }
        if ( System.getProperty( RunLogFile.LOG_FILE_PROPERTY ) == null ) {
            System.setProperty( "log4j2.configurationFile", "log4j2-console.xml" );
        }
        System.exit( run( installation, arguments ) );
    }

    private static int run( Path installation, String[] arguments ) {
        UserConsole console = new UserConsole();
        LauncherArguments parsed;
        try {
            parsed = LauncherArguments.parse( arguments );
        } catch ( IllegalArgumentException exception ) {
            console.showErrorAndWait( "Nieprawidłowe wywołanie launchera vDeck." );
            return 2;
        }
        if ( installation == null ) {
            console.showErrorAndWait( "Nie można ustalić katalogu instalacji vDeck." );
            return 2;
        }
        UpdateLayout layout = new UpdateLayout( installation );
        return new VdeckLauncher( layout, console, new ServerActivityCheck( installation ),
                new HandoffProtocol( installation ), new SuccessorStarter( installation ),
                ReleaseVersion.parse( LauncherMain.class.getPackage().getImplementationVersion() ),
                installed -> preparation( layout, installed ) ).run( parsed );
    }

    private static UpdatePreparation preparation( UpdateLayout layout, ReleaseVersion installed ) {
        // NORMAL follows GitHub's redirect to its download host but never from HTTPS to HTTP.
        HttpClient client = HttpClient.newBuilder().connectTimeout( CONNECT_TIMEOUT )
                .followRedirects( HttpClient.Redirect.NORMAL ).build();
        return new UpdatePreparation( layout, client,
                new GitHubReleaseClient( client, GitHubReleaseClient.LATEST_RELEASE, installed ),
                new ReleaseDownloader( client ), new SafeZipExtractor(), new UpdateExecutorPreparation(),
                UpdatePreparation.BUDGET );
    }

    private static Path resolve( String directory ) {
        try {
            return Path.of( directory ).toRealPath();
        } catch ( IOException | RuntimeException exception ) {
            return null;
        }
    }
}
