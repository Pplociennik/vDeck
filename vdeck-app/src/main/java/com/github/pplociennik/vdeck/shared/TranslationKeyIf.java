package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;

/**
 * A single text of the user interface, identified by its key in the translation bundle.
 * Implementations are enums, one per screen or feature, so the set of texts a component can produce
 * is closed and known at compile time instead of being spread over string literals.
 *
 * <p>Java code never carries the text itself: it produces a key, and the wording is resolved from the
 * translation bundle. This applies to exceptions whose message reaches the user as well — such an
 * exception takes the key in its constructor.
 */
public interface TranslationKeyIf {

    /**
     * Returns the literal key this text has in the translation bundle.
     * The key is never blank and resolves in the {@code messages.properties} bundle shipped with the
     * application.
     *
     * @return the translation file key of this text
     */
    @Nonnull
    String getKey();
}
