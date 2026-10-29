package com.github.pplociennik.vdeck.feature.players;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PlayerListExceptionHandler} redirects back to the list's panel with the message and,
 * for an addition, the submitted entry as flash attributes, under the names the panel reads.
 */
class PlayerListExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private PlayerListExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh advice before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new PlayerListExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rejected addition returns to the list's panel with the message and the submitted entry.
     */
    @Test
    void shouldRedirectToListWithFlashedForm_whenAdditionIsRejected() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, "123" );
        PlayerListUpdateException exception =
                PlayerListUpdateException.rejected( PlayerListMessage.INVALID_USER_ID, PlayerList.PERMITTED, form );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleChangeNotStored( exception, redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/players/permitted" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsEntry( "playerEntryForm", form )
                .containsEntry( "playerListErrorKey", PlayerListMessage.INVALID_USER_ID.getKey() );
        assertThat( redirectAttributes ).isEmpty();
    }

    /**
     * Verifies that a failed removal returns to the list's panel with only the message.
     */
    @Test
    void shouldRedirectToListWithMessageOnly_whenRemovalFails() {
        // GIVEN
        PlayerListUpdateException exception =
                PlayerListUpdateException.saveFailed( PlayerList.BANNED, null, new IOException( "disk full" ) );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleChangeNotStored( exception, redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/players/bans" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsOnly( Map.entry( "playerListErrorKey", PlayerListMessage.SAVE_FAILED.getKey() ) );
    }

    /**
     * Verifies that the advice refuses to run without the exception it answers or the attributes it fills.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        PlayerListUpdateException exception =
                PlayerListUpdateException.rejected( PlayerListMessage.NOT_LISTED, PlayerList.ADMINS, null );

        // WHEN
        Throwable missingException =
                catchThrowable( () -> underTest.handleChangeNotStored( null, new RedirectAttributesModelMap() ) );
        Throwable missingAttributes = catchThrowable( () -> underTest.handleChangeNotStored( exception, null ) );

        // THEN
        assertThat( missingException ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "exception" );
        assertThat( missingAttributes ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "redirectAttributes" );
    }
}
