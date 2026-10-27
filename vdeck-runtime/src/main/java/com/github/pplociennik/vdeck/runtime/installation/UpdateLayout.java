package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Fixed locations used by the launcher, updater and recovery inside one installation (AD-024).
 * The start script relies on the journal and executor paths without parsing anything, so these paths
 * are part of the recovery protocol and never change within {@link #RECOVERY_PROTOCOL}.
 *
 * <pre>
 * .vDeck/handoff/              one-time handoff descriptors and acknowledgements
 * .vDeck/update/download/      downloaded package and its .part file
 * .vDeck/update/staged/        verified package contents, the source of the replacement
 * .vDeck/update/merged/        merged settings files waiting for their journaled replacement
 * .vDeck/update/executor/      independent copy of the JRE and the updater JAR
 * .vDeck/update/backup/        originals of the last transaction
 * .vDeck/update/apply.journal  recovery entry point while a transaction is not cleaned up
 * </pre>
 *
 * @param installation
 *         canonical absolute installation root
 */
public record UpdateLayout( @Nonnull Path installation ) {

    /**
     * Version of the contract between the start script, the journal header and the executor layout.
     * A package declaring another version is rejected before anything is replaced.
     */
    public static final int RECOVERY_PROTOCOL = 1;

    /**
     * Manifest attribute declaring the recovery protocol supported by an updater JAR.
     */
    public static final String RECOVERY_PROTOCOL_ATTRIBUTE = "Vdeck-Recovery-Protocol";

    /**
     * Panel-owned directory directly below the installation root.
     */
    public static final String PANEL_DIRECTORY = ".vDeck";

    /**
     * Installed launcher JAR, relative to the installation root.
     */
    public static final String LAUNCHER_JAR = "lib/vdeck-launcher.jar";

    /**
     * Installed updater JAR, relative to the installation root.
     */
    public static final String UPDATER_JAR = "lib/vdeck-updater.jar";

    /**
     * Installed panel JAR, relative to the installation root.
     */
    public static final String PANEL_JAR = "lib/vdeck.jar";

    /**
     * Java launcher of the bundled runtime, relative to a runtime root such as the installation.
     */
    public static final String JAVA_EXECUTABLE = "jre/bin/java.exe";

    /**
     * JVM options of the launcher and updater, which only wait, copy and download: a small heap and
     * no optimizing compiler keep their memory footprint low while the panel runs (AD-001, AD-024).
     */
    public static final List< String > SMALL_JVM_OPTIONS = List.of( "-Xmx32m", "-XX:+UseSerialGC",
            "-XX:TieredStopAtLevel=1" );

    /**
     * System property naming the installation directory of every vDeck JVM.
     */
    public static final String SERVER_DIRECTORY_PROPERTY = "vdeck.server-directory";

    /**
     * System property through which the panel receives its handoff identifier from the launcher.
     */
    public static final String PANEL_HANDOFF_PROPERTY = "vdeck.handoff";

    /**
     * Validates the root.
     *
     * @throws NullPointerException
     *         if the installation is null
     * @throws IllegalArgumentException
     *         if the installation is relative
     */
    public UpdateLayout {
        requireNonNull( installation, "installation must not be null" );
        if ( !installation.isAbsolute() ) {
            throw new IllegalArgumentException( "installation must be absolute" );
        }
    }

    /**
     * Returns the panel's {@code .vDeck} directory.
     *
     * @return absolute panel directory
     */
    public @Nonnull Path panelDirectory() {
        return installation.resolve( PANEL_DIRECTORY );
    }

    /**
     * Returns the directory of handoff descriptors.
     *
     * @return absolute handoff directory
     */
    public @Nonnull Path handoffDirectory() {
        return panelDirectory().resolve( "handoff" );
    }

    /**
     * Returns the directory holding every update work file.
     *
     * @return absolute update directory
     */
    public @Nonnull Path updateDirectory() {
        return panelDirectory().resolve( "update" );
    }

    /**
     * Returns the download directory.
     *
     * @return absolute download directory
     */
    public @Nonnull Path downloadDirectory() {
        return updateDirectory().resolve( "download" );
    }

    /**
     * Returns the directory of the verified package contents.
     *
     * @return absolute staged directory
     */
    public @Nonnull Path stagedDirectory() {
        return updateDirectory().resolve( "staged" );
    }

    /**
     * Returns the directory of merged settings files.
     *
     * @return absolute merged directory
     */
    public @Nonnull Path mergedDirectory() {
        return updateDirectory().resolve( "merged" );
    }

    /**
     * Returns the independent runtime used to execute the updater.
     *
     * @return absolute executor directory
     */
    public @Nonnull Path executorDirectory() {
        return updateDirectory().resolve( "executor" );
    }

    /**
     * Returns the Java launcher of the executor runtime.
     *
     * @return absolute executor {@code java.exe}
     */
    public @Nonnull Path executorJava() {
        return executorDirectory().resolve( JAVA_EXECUTABLE );
    }

    /**
     * Returns the updater JAR of the executor runtime.
     *
     * @return absolute executor updater JAR
     */
    public @Nonnull Path executorUpdaterJar() {
        return executorDirectory().resolve( "vdeck-updater.jar" );
    }

    /**
     * Returns the directory of originals saved by the last transaction.
     *
     * @return absolute backup directory
     */
    public @Nonnull Path backupDirectory() {
        return updateDirectory().resolve( "backup" );
    }

    /**
     * Returns the transaction journal, whose existence alone makes the start script run recovery.
     *
     * @return absolute journal path
     */
    public @Nonnull Path journal() {
        return updateDirectory().resolve( "apply.journal" );
    }

    /**
     * Returns the installed Java launcher.
     *
     * @return absolute installed {@code java.exe}
     */
    public @Nonnull Path installedJava() {
        return installation.resolve( JAVA_EXECUTABLE );
    }

    /**
     * Resolves a path that is relative to the installation root.
     *
     * @param relative
     *         forward-slash separated relative path from a closed list
     * @return absolute path inside the installation
     * @throws NullPointerException
     *         if the path is null
     */
    public @Nonnull Path resolve( @Nonnull String relative ) {
        requireNonNull( relative, "relative must not be null" );
        return installation.resolve( relative );
    }
}
