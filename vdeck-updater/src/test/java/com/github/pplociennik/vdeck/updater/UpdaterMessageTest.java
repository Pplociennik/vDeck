package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.I18n;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every updater console message has a text in the console bundle.
 */
class UpdaterMessageTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the message key resolves to a text.
     *
     * @param message
     *         updater console message
     */
    @ParameterizedTest( name = "[{index}] message={0}" )
    @EnumSource( UpdaterMessage.class )
    void shouldResolveText_whenMessageIsShown( UpdaterMessage message ) {
        // GIVEN
        // The bundle is on the classpath.

        // WHEN
        String text = I18n.text( message );

        // THEN
        assertThat( text ).isNotBlank().isNotEqualTo( message.getKey() );
    }
}
