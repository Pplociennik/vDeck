package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of messages the server control feature shows to the administrator. Each constant names a
 * single, actionable reason a start was refused or failed, so the panel never has to explain a problem
 * with a text assembled in Java.
 */
public enum ServerMessage implements TranslationKeyIf {

    /**
     * A start was requested while a server is already starting or running.
     */
    ALREADY_RUNNING( "server.error.alreadyRunning" ),

    /**
     * {@code valheim_server.exe} is missing from the installation directory, or is not a regular file.
     */
    EXECUTABLE_MISSING( "server.error.executableMissing" ),

    /**
     * A mandatory launch profile setting is empty, so the server has nothing to start with.
     */
    PROFILE_INCOMPLETE( "server.error.profileIncomplete" ),

    /**
     * The server password is shorter than the five characters Valheim demands.
     */
    PASSWORD_TOO_SHORT( "server.error.passwordTooShort" ),

    /**
     * The server password occurs inside the server name, which Valheim refuses to start with.
     */
    PASSWORD_PART_OF_NAME( "server.error.passwordPartOfName" ),

    /**
     * The server is public but has no password, which Valheim does not allow.
     */
    PUBLIC_REQUIRES_PASSWORD( "server.error.publicRequiresPassword" ),

    /**
     * One of the two consecutive UDP ports the server needs is already taken.
     */
    PORT_IN_USE( "server.error.portInUse" ),

    /**
     * The configured world directory cannot be created or written to.
     */
    SAVE_DIRECTORY_NOT_WRITABLE( "server.error.saveDirectoryNotWritable" ),

    /**
     * The configured log directory cannot be created or written to.
     */
    LOG_DIRECTORY_NOT_WRITABLE( "server.error.logDirectoryNotWritable" ),

    /**
     * Every check passed but the operating system refused to create the process.
     */
    START_FAILED( "server.error.startFailed" ),

    /** Stale launch, invalid state, or concurrent work refused a stop. */
    STOP_REJECTED( "server.error.stopRejected" ),
    /** A confirmed Kill request could not be admitted. */
    KILL_REJECTED( "server.error.killRejected" ),
    /** Force permission was not explicitly accepted. */
    CONFIRMATION_REQUIRED( "server.error.confirmationRequired" ),
    /** The selected saved launch cannot be adopted. */
    ADOPT_REJECTED( "server.error.adoptRejected" ),
    /** Adoption admission failed technically. */
    ADOPT_FAILED( "server.error.adoptFailed" ),
    /** Runtime stop admission failed technically. */
    STOP_FAILED( "server.error.stopFailed" );

    private final String key;

    ServerMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
