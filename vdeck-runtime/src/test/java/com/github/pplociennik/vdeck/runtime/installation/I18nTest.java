package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that console texts are resolved from the bundle and formatted with the Polish locale.
 */
class I18nTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every shared console message has a text in the bundle.
     *
     * @param message
     *         shared console message
     */
    @ParameterizedTest( name = "[{index}] message={0}" )
    @EnumSource( ConsoleMessage.class )
    void shouldResolveText_whenSharedMessageIsShown( ConsoleMessage message ) {
        // GIVEN
        // The bundle is on the classpath.

        // WHEN
        String text = I18n.text( message );

        // THEN
        assertThat( text ).isNotBlank().isNotEqualTo( message.getKey() );
    }

    /**
     * Verifies that Polish characters escaped in the bundle are decoded.
     */
    @Test
    void shouldDecodePolishCharacters_whenTextIsResolved() {
        // GIVEN
        // The bundle stores the text with escape sequences.

        // WHEN
        String text = I18n.text( ConsoleMessage.PRESS_ENTER );

        // THEN
        assertThat( text ).isEqualTo( "Naciśnij Enter, aby zamknąć okno." );
    }

    /**
     * Verifies that decimals use a Polish comma and integers are formatted without grouping.
     */
    @Test
    void shouldFormatNumbersInPolish_whenArgumentsAreGiven() {
        // GIVEN
        ConsoleMessageIf progress = () -> "launcher.downloadProgress";
        ConsoleMessageIf address = () -> "launcher.panelAddress";

        // WHEN
        String progressText = I18n.text( progress, 1.5, 3.0, 50L, 0.5 );
        String addressText = I18n.text( address, "localhost", 12345 );

        // THEN
        assertThat( progressText ).isEqualTo( "Pobieranie: 1,5 z 3,0 MB (50%), 0,5 MB/s" );
        assertThat( addressText ).isEqualTo( "Panel vDeck: http://localhost:12345" );
    }

    /**
     * Verifies that a key missing from the bundle is returned as the text instead of failing.
     */
    @Test
    void shouldReturnKey_whenBundleLacksIt() {
        // GIVEN
        ConsoleMessageIf missing = () -> "console.missing";

        // WHEN
        String text = I18n.text( missing );

        // THEN
        assertThat( text ).isEqualTo( "console.missing" );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingMessage = catchThrowable( () -> I18n.text( null ) );
        var missingArguments = catchThrowable( () -> I18n.text( ConsoleMessage.PRESS_ENTER, ( Object[] ) null ) );

        // THEN
        assertThat( missingMessage ).isInstanceOf( NullPointerException.class );
        assertThat( missingArguments ).isInstanceOf( NullPointerException.class );
    }
}
