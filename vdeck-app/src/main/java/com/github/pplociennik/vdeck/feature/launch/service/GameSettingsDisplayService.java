package com.github.pplociennik.vdeck.feature.launch.service;

/**
 * Defines the remembered display state of the "Ustawienia gry" screen: whether its advanced section is
 * shown. The state is a panel preference stored in {@code vDeck.properties}, not part of the launch
 * profile, so hiding the section never changes how the server is started.
 */
public interface GameSettingsDisplayService {

    /**
     * Reports whether the advanced section is shown.
     *
     * @return {@code true} when the administrator last chose to show the section; {@code false} by default
     */
    boolean isAdvancedShown();

    /**
     * Remembers whether the advanced section is shown.
     *
     * @param shown
     *         whether the section is shown
     * @return {@code true} when the choice was stored, {@code false} when the file could not be written
     */
    boolean setAdvancedShown( boolean shown );
}
