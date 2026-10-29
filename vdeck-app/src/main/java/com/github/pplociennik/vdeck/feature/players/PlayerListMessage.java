package com.github.pplociennik.vdeck.feature.players;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of messages a player list panel can show after a change was not stored.
 * Every constant carries only the translation key, so the wording stays in the translation file.
 */
public enum PlayerListMessage implements TranslationKeyIf {

    /**
     * Submitted fields did not pass validation: the platform or the user id is missing, or the id holds
     * something other than digits.
     */
    INVALID_INPUT( "players.error.invalidInput" ),

    /**
     * The user id does not have the format of the chosen platform.
     */
    INVALID_USER_ID( "players.error.invalidUserId" ),

    /**
     * The player is already on the list.
     */
    ALREADY_LISTED( "players.error.alreadyListed" ),

    /**
     * The player to remove is no longer on the list, for example because the file was changed meanwhile.
     */
    NOT_LISTED( "players.error.notListed" ),

    /**
     * The list file could not be read or written.
     */
    SAVE_FAILED( "players.error.saveFailed" );

    private final String key;

    PlayerListMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Returns the translation key of this message.
     *
     * @return key resolved against the translation file
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
