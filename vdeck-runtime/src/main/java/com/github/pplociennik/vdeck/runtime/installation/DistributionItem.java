package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import lombok.Getter;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Closed list of everything a distribution package owns in an installation (AD-024). The launcher accepts
 * only ZIP entries belonging to these items and the updater replaces only these paths; the database,
 * logs, snapshots, recovery token, update work files and Valheim files are never part of an update.
 *
 * <p>Order is the order of application; rollback walks it backwards.
 */
@Getter
public enum DistributionItem {

    /**
     * Application JARs: panel, CTRL+C helper, launcher and updater.
     */
    LIB( "lib", Kind.DIRECTORY ),

    /**
     * Bundled Java runtime.
     */
    JRE( "jre", Kind.DIRECTORY ),

    /**
     * Start script; replaced atomically and never absent (AD-026).
     */
    START_SCRIPT( "start-vDeck.bat", Kind.FILE ),

    /**
     * Markdown readme.
     */
    README_MD( "README.md", Kind.FILE ),

    /**
     * Plain-text readme.
     */
    README_TXT( "README.txt", Kind.FILE ),

    /**
     * User manual (AD-022).
     */
    MANUAL( "vDeck-instrukcja.pdf", Kind.FILE ),

    /**
     * Panel settings, merged with the user's values (AD-025).
     */
    PANEL_SETTINGS( ".vDeck/config/vDeck.properties", Kind.SETTINGS ),

    /**
     * Valheim launch profile, merged with the user's values (AD-025).
     */
    SERVER_SETTINGS( ".vDeck/config/valheim-server.properties", Kind.SETTINGS ),

    /**
     * Launcher start settings, merged with the user's values (AD-025, AD-026).
     */
    START_SETTINGS( ".vDeck/config/vDeck-start.properties", Kind.SETTINGS );

    /**
     * Files that must exist in a complete package or installation, as forward-slash relative paths.
     */
    public static final List< String > REQUIRED_FILES = List.of( UpdateLayout.PANEL_JAR, "lib/vdeck-runner.jar",
            UpdateLayout.LAUNCHER_JAR, UpdateLayout.UPDATER_JAR, UpdateLayout.JAVA_EXECUTABLE, "jre/bin/javaw.exe",
            "start-vDeck.bat", "README.md", "README.txt", "vDeck-instrukcja.pdf", ".vDeck/config/vDeck.properties",
            ".vDeck/config/valheim-server.properties", ".vDeck/config/vDeck-start.properties" );

    /**
     * Forward-slash path relative to the installation root and to the package root.
     */
    private final String path;

    /**
     * How the item is replaced.
     */
    private final Kind kind;

    DistributionItem( String path, Kind kind ) {
        this.path = path;
        this.kind = kind;
    }

    /**
     * Finds the item with exactly the given relative path.
     *
     * @param path
     *         forward-slash relative path
     * @return matching item, or an empty optional for a path outside the closed list
     * @throws NullPointerException
     *         if the path is null
     */
    public static @Nonnull Optional< DistributionItem > ofPath( @Nonnull String path ) {
        requireNonNull( path, "path must not be null" );
        for ( DistributionItem item : values() ) {
            if ( item.path.equals( path ) ) {
                return Optional.of( item );
            }
        }
        return Optional.empty();
    }

    /**
     * Lists the required files missing below a package or installation root. Links count as missing.
     *
     * @param root
     *         package or installation root
     * @return missing relative paths in {@link #REQUIRED_FILES} order; empty when complete
     * @throws NullPointerException
     *         if the root is null
     */
    public static @Nonnull List< String > missingFiles( @Nonnull Path root ) {
        requireNonNull( root, "root must not be null" );
        List< String > missing = new ArrayList<>();
        for ( String file : REQUIRED_FILES ) {
            if ( !Files.isRegularFile( root.resolve( file ), LinkOption.NOFOLLOW_LINKS ) ) {
                missing.add( file );
            }
        }
        return List.copyOf( missing );
    }

    /**
     * Replacement strategies of distribution items.
     */
    public enum Kind {

        /**
         * Whole directory moved to the backup and replaced by the staged directory.
         */
        DIRECTORY,

        /**
         * Single file copied to the backup and replaced atomically by the staged file.
         */
        FILE,

        /**
         * Settings file replaced atomically by the merge of the staged template and the user's file.
         */
        SETTINGS
    }
}
