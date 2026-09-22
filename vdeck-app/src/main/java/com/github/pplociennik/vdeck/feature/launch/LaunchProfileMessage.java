package com.github.pplociennik.vdeck.feature.launch;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of messages the launch profile screen can show after a rejected update.
 * Every constant carries only the translation key, so the wording stays in the translation file.
 */
public enum LaunchProfileMessage implements TranslationKeyIf {

    /**
     * Submitted fields did not pass validation: a value is missing, out of range, badly formatted or
     * outside the options the server accepts.
     */
    INVALID_INPUT( "launchProfile.error.invalidInput" ),

    /**
     * The save directory is not a relative path inside the installation directory.
     */
    INVALID_SAVE_DIRECTORY( "launchProfile.error.invalidSaveDirectory" ),

    /**
     * The server password is a part of the server name, which the Valheim server refuses.
     */
    PASSWORD_PART_OF_NAME( "launchProfile.error.passwordPartOfName" ),

    /**
     * The profile could not be written to its file.
     */
    SAVE_FAILED( "launchProfile.error.saveFailed" );

    private final String key;

    LaunchProfileMessage( @Nonnull String key ) {
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