package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the stable journal header shared by every launcher and updater.
 */
class UpdateJournalHeaderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private UpdateJournalHeader underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a header of an applying transaction.
     */
    @BeforeEach
    void setUp() {
        underTest = new UpdateJournalHeader( UUID.fromString( "11111111-2222-3333-4444-555555555555" ),
                UpdateState.APPLYING );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a written header is parsed back unchanged together with the protocol version.
     */
    @Test
    void shouldRoundTripHeader_whenWrittenAndParsed() throws Exception {
        // GIVEN
        Properties values = new Properties();

        // WHEN
        underTest.writeTo( values );
        UpdateJournalHeader parsed = UpdateJournalHeader.parse( values );

        // THEN
        assertThat( parsed ).isEqualTo( underTest );
        assertThat( values ).containsEntry( "protocol", "1" );
    }

    /**
     * Verifies that a journal of another recovery protocol is refused.
     */
    @Test
    void shouldThrowException_whenProtocolDiffers() {
        // GIVEN
        Properties values = new Properties();
        underTest.writeTo( values );
        values.setProperty( "protocol", "2" );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournalHeader.parse( values ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "protocol" );
    }

    /**
     * Verifies that an unknown state is reported as a malformed journal.
     */
    @Test
    void shouldThrowException_whenStateIsUnknown() {
        // GIVEN
        Properties values = new Properties();
        underTest.writeTo( values );
        values.setProperty( "state", "DONE" );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournalHeader.parse( values ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "Malformed" );
    }

    /**
     * Verifies that a missing identifier is reported.
     */
    @Test
    void shouldThrowException_whenUpdateIdIsMissing() {
        // GIVEN
        Properties values = new Properties();
        underTest.writeTo( values );
        values.remove( "updateId" );

        // WHEN
        var failure = catchThrowable( () -> UpdateJournalHeader.parse( values ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "updateId" );
    }

    /**
     * Verifies that only committed and rolled-back transactions are terminal.
     *
     * @param state
     *         state being classified
     */
    @ParameterizedTest( name = "[{index}] state={0}" )
    @EnumSource( UpdateState.class )
    void shouldClassifyTerminalStates_whenAskedForTermination( UpdateState state ) {
        // GIVEN
        boolean expected = state == UpdateState.COMMITTED || state == UpdateState.ROLLED_BACK;

        // WHEN
        boolean terminal = state.isTerminal();

        // THEN
        assertThat( terminal ).isEqualTo( expected );
    }

    /**
     * Verifies that null components and arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingId = catchThrowable( () -> new UpdateJournalHeader( null, UpdateState.PREPARED ) );
        var missingValues = catchThrowable( () -> UpdateJournalHeader.parse( null ) );
        var missingJournal = catchThrowable( () -> UpdateJournalHeader.read( null ) );

        // THEN
        assertThat( missingId ).isInstanceOf( NullPointerException.class );
        assertThat( missingValues ).isInstanceOf( NullPointerException.class );
        assertThat( missingJournal ).isInstanceOf( NullPointerException.class );
    }
}
