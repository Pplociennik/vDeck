package com.github.pplociennik.vdeck.feature.server.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Values of the {@code panel.close.strategy} setting, each naming the {@link PanelCloseStrategy} applied to the
 * managed Valheim server when the panel closes (AD-031).
 */
public enum PanelCloseMode {

    /**
     * Graceful stop; a process that survives it is forced to exit after a snapshot attempt. The default.
     */
    STOP( "stop" ),

    /**
     * Graceful stop only; a process that survives it keeps running and its token allows explicit adoption.
     */
    GRACEFUL( "graceful" ),

    /**
     * No stop at all; the server keeps running and the next panel offers its explicit adoption.
     */
    LEAVE( "leave" );

    private final String value;

    PanelCloseMode( @Nonnull String value ) {
        this.value = requireNonNull( value, "value must not be null" );
    }

    /**
     * Finds the mode stored in {@code vDeck.properties} as the given value. The comparison ignores letter
     * case and surrounding white space, because the file can be edited by hand.
     *
     * @param value
     *         value to look up; {@code null} finds nothing
     * @return the matching mode, or an empty result when the value names none
     */
    public static @Nonnull Optional< PanelCloseMode > fromValue( @Nullable String value ) {
        if ( value == null ) {
            return Optional.empty();
        }
        String strippedValue = value.strip();
        for ( PanelCloseMode mode : values() ) {
            if ( mode.value.equalsIgnoreCase( strippedValue ) ) {
                return Optional.of( mode );
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the value of this mode as stored in {@code vDeck.properties}.
     *
     * @return lower-case setting value
     */
    public @Nonnull String getValue() {
        return value;
    }
}
