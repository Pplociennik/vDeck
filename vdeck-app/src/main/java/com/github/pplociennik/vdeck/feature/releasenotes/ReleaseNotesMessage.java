package com.github.pplociennik.vdeck.feature.releasenotes;

import com.github.pplociennik.vdeck.shared.TranslationKeyIf;
import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Closed set of messages the change list panel can show to the administrator when closing it did not
 * succeed.
 */
public enum ReleaseNotesMessage implements TranslationKeyIf {

    /**
     * The acknowledgement could not be stored, so the change list will be shown again.
     */
    ACKNOWLEDGEMENT_FAILED( "releaseNotes.error.acknowledgementFailed" );

    private final String key;

    ReleaseNotesMessage( @Nonnull String key ) {
        this.key = requireNonNull( key, "key must not be null" );
    }

    /**
     * Returns the translation key of this message.
     *
     * @return key resolved against the translation files
     */
    @Override
    public @Nonnull String getKey() {
        return key;
    }
}
