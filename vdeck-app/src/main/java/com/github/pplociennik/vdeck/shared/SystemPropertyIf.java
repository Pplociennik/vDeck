package com.github.pplociennik.vdeck.shared;

import jakarta.annotation.Nonnull;

/**
 * A single setting stored in one of the external vDeck properties files.
 * Implementations are enums, one per properties file, so that the complete set of settings owned by
 * a file is closed and known at compile time.
 *
 * <p>This interface deliberately carries nothing but the key. Default values, the owning file and
 * any interpretation of the value belong to the concrete enum and to the service handling that file,
 * which keeps the accessor signature usable for every future properties file.
 */
public interface SystemPropertyIf {

    /**
     * Returns the literal key this setting has in its properties file.
     * The name is never blank and is unique within a single file.
     *
     * @return the properties file key of this setting
     */
    @Nonnull
    String getName();
}
