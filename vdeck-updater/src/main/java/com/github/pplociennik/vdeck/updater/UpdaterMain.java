package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.HandoffProtocol;
import com.github.pplociennik.vdeck.runtime.installation.RunLogFile;
import com.github.pplociennik.vdeck.runtime.installation.ServerActivityCheck;
import com.github.pplociennik.vdeck.runtime.installation.SuccessorStarter;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import com.github.pplociennik.vdeck.runtime.installation.UserConsole;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * Bootstrap of the updater JAR. It deliberately has no logger: the run log file must be chosen and
 * published as a system property before the first logger initializes Log4j2 (AD-024).
 */
public final class UpdaterMain {

    private UpdaterMain() {
    }

    /**
     * Creates this run's log file below {@code .vDeck/logs/updater} and runs the updater. Exits with 0
     * after handing the installation to the launcher, 1 on failure and 2 for an invalid invocation.
     *
     * @param arguments
     *         {@code [--recover] [--handoff <id>] [-- <panel arguments>]}
     * @throws NullPointerException
     *         if the arguments are null
     */
    public static void main( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        String directory = System.getProperty( UpdateLayout.SERVER_DIRECTORY_PROPERTY );
        Path installation = directory == null ? null : resolve( directory );
        if ( installation != null ) {
            RunLogFile.create( installation, "updater", LocalDateTime.now() ).ifPresent(
                    file -> System.setProperty( RunLogFile.LOG_FILE_PROPERTY, file.toString() ) );
        }
        if ( System.getProperty( RunLogFile.LOG_FILE_PROPERTY ) == null ) {
            System.setProperty( "log4j2.configurationFile", "log4j2-console.xml" );
        }
        System.exit( run( installation, arguments ) );
    }

    private static int run( Path installation, String[] arguments ) {
        UserConsole console = new UserConsole();
        UpdaterArguments parsed;
        try {
            parsed = UpdaterArguments.parse( arguments );
        } catch ( IllegalArgumentException exception ) {
            console.showErrorAndWait( "Nieprawidłowe wywołanie aktualizatora vDeck." );
            return 2;
        }
        if ( installation == null ) {
            console.showErrorAndWait( "Nie można ustalić katalogu instalacji vDeck." );
            return 2;
        }
        String version = UpdaterMain.class.getPackage().getImplementationVersion();
        return new VdeckUpdater( new UpdateLayout( installation ), console, new ServerActivityCheck( installation ),
                new HandoffProtocol( installation ), new SuccessorStarter( installation ),
                version == null ? "unknown" : version, Checkpoint.NONE ).run( parsed );
    }

    private static Path resolve( String directory ) {
        try {
            return Path.of( directory ).toRealPath();
        } catch ( IOException | RuntimeException exception ) {
            return null;
        }
    }
}
