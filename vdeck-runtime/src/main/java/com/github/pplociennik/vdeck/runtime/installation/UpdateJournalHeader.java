package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Stable header of {@code apply.journal}, readable by every launcher and updater of
 * {@link UpdateLayout#RECOVERY_PROTOCOL} without interpreting the operations that follow it.
 *
 * @param updateId
 *         identifier of the transaction
 * @param state
 *         current transaction state
 */
public record UpdateJournalHeader( @Nonnull UUID updateId, @Nonnull UpdateState state ) {

    /**
     * Largest accepted journal size in bytes; the journal is untrusted input.
     */
    public static final int MAX_JOURNAL_BYTES = 64 * 1024;

    /**
     * Journal key holding the recovery protocol version.
     */
    public static final String PROTOCOL_KEY = "protocol";

    /**
     * Journal key holding the transaction identifier.
     */
    public static final String UPDATE_ID_KEY = "updateId";

    /**
     * Journal key holding the transaction state.
     */
    public static final String STATE_KEY = "state";

    /**
     * Validates the header.
     *
     * @throws NullPointerException
     *         if a component is null
     */
    public UpdateJournalHeader {
        requireNonNull( updateId, "updateId must not be null" );
        requireNonNull( state, "state must not be null" );
    }

    /**
     * Reads and validates the header of a journal file.
     *
     * @param journal
     *         journal file
     * @return validated header
     * @throws NullPointerException
     *         if the journal is null
     * @throws IOException
     *         if the file is unreadable, too large, of another protocol or malformed
     */
    public static @Nonnull UpdateJournalHeader read( @Nonnull Path journal ) throws IOException {
        requireNonNull( journal, "journal must not be null" );
        return parse( DurableFiles.readProperties( journal, MAX_JOURNAL_BYTES ) );
    }

    /**
     * Validates the header entries of already loaded journal properties.
     *
     * @param values
     *         loaded journal
     * @return validated header
     * @throws NullPointerException
     *         if the values are null
     * @throws IOException
     *         if the protocol differs or an entry is missing or malformed
     */
    public static @Nonnull UpdateJournalHeader parse( @Nonnull Properties values ) throws IOException {
        requireNonNull( values, "values must not be null" );
        if ( !Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ).equals( values.getProperty( PROTOCOL_KEY ) ) ) {
            throw new IOException( "Unsupported update journal protocol" );
        }
        try {
            return new UpdateJournalHeader( UUID.fromString( required( values, UPDATE_ID_KEY ) ),
                    UpdateState.valueOf( required( values, STATE_KEY ) ) );
        } catch ( IllegalArgumentException exception ) {
            throw new IOException( "Malformed update journal header", exception );
        }
    }

    /**
     * Writes the header entries into journal properties.
     *
     * @param values
     *         journal properties to fill
     * @throws NullPointerException
     *         if the values are null
     */
    public void writeTo( @Nonnull Properties values ) {
        requireNonNull( values, "values must not be null" );
        values.setProperty( PROTOCOL_KEY, Integer.toString( UpdateLayout.RECOVERY_PROTOCOL ) );
        values.setProperty( UPDATE_ID_KEY, updateId.toString() );
        values.setProperty( STATE_KEY, state.name() );
    }

    private static String required( Properties values, String key ) throws IOException {
        String value = values.getProperty( key );
        if ( value == null ) {
            throw new IOException( "Missing update journal entry " + key );
        }
        return value;
    }
}
