package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Console texts shared by the launcher, the updater and the panel.
 */
public enum ConsoleMessage implements ConsoleMessageIf {

    /**
     * Asks for Enter before an error closes the console window.
     */
    PRESS_ENTER( "console.pressEnter" ),

    /**
     * The installation directory property is missing or cannot be resolved.
     */
    INSTALLATION_DIRECTORY_UNKNOWN( "console.installationDirectoryUnknown" ),

    /**
     * Address of the started panel; arguments: host and port.
     */
    PANEL_ADDRESS( "console.panelAddress" ),

    /**
     * Asks to keep the console window open while the panel runs.
     */
    KEEP_WINDOW_OPEN( "console.keepWindowOpen" );

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
