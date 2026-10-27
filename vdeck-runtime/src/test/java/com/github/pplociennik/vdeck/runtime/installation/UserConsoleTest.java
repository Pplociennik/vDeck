package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the console messages and the Enter confirmation of errors.
 */
class UserConsoleTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private UserConsole underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an error waits exactly for the first line and leaves the rest of the input unread.
     */
    @Test
    void shouldConsumeOneLine_whenShowingError() {
        // GIVEN
        ByteArrayInputStream input = new ByteArrayInputStream( "first\nsecond".getBytes( StandardCharsets.UTF_8 ) );
        underTest = new UserConsole( input );

        // WHEN
        underTest.showErrorAndWait( "Błąd" );

        // THEN
        assertThat( input.available() ).isEqualTo( "second".length() );
    }

    /**
     * Verifies that a closed input never blocks an error.
     */
    @Test
    void shouldReturn_whenInputIsExhausted() {
        // GIVEN
        underTest = new UserConsole( new ByteArrayInputStream( new byte[ 0 ] ) );

        // WHEN
        var action = ( Runnable ) () -> underTest.showErrorAndWait( "Błąd" );

        // THEN
        assertThatCode( action::run ).doesNotThrowAnyException();
    }

    /**
     * Verifies that null messages and inputs are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        underTest = new UserConsole( new ByteArrayInputStream( new byte[ 0 ] ) );

        // WHEN
        var missingMessage = catchThrowable( () -> underTest.show( null ) );
        var missingInput = catchThrowable( () -> new UserConsole( null ) );

        // THEN
        assertThat( missingMessage ).isInstanceOf( NullPointerException.class );
        assertThat( missingInput ).isInstanceOf( NullPointerException.class );
    }
}
