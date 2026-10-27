package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Console texts shared by the launcher and the updater.
 */
public enum ConsoleMessage implements ConsoleMessageIf {

    /**
     * Asks for Enter before an error closes the console window.
     */
    PRESS_ENTER( "console.pressEnter" ),

    /**
     * The installation directory property is missing or cannot be resolved.
     */
    INSTALLATION_DIRECTORY_UNKNOWN( "console.installationDirectoryUnknown" );

    private final String key;

    ConsoleMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Returns the translation key of this message.
     *
     * @return key resolved against the console translation bundle
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
