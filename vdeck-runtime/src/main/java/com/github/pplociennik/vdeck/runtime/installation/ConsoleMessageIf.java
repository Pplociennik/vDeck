package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

/**
 * A single console text of the launcher or updater, identified by its key in the
 * {@value I18n#BUNDLE} bundle. Implementations are enums, one per program, so the set of texts a program
 * can show is closed and known at compile time instead of being spread over string literals.
 */
public interface ConsoleMessageIf {

    /**
     * Returns the literal key this text has in the console translation bundle.
     *
     * @return the never blank translation file key of this text
     */
    @Nonnull
    String getKey();
}
