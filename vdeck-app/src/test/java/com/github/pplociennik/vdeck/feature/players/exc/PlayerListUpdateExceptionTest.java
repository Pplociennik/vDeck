package com.github.pplociennik.vdeck.feature.players.exc;

import com.github.pplociennik.vdeck.feature.players.PlayerListMessage;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the two kinds of {@link PlayerListUpdateException}: a rejection without a cause and a failure
 * keeping its cause.
 */
class PlayerListUpdateExceptionTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rejection keeps the message, the list and the submitted form, and has no cause.
     */
    @Test
    void shouldCarryMessageListAndForm_whenChangeIsRejected() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, "1" );

        // WHEN
        PlayerListUpdateException result =
                PlayerListUpdateException.rejected( PlayerListMessage.INVALID_USER_ID, PlayerList.ADMINS, form );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( PlayerListMessage.INVALID_USER_ID );
        assertThat( result.getPlayerList() ).isEqualTo( PlayerList.ADMINS );
        assertThat( result.getSubmittedForm() ).isEqualTo( form );
        assertThat( result.isFailure() ).isFalse();
        assertThat( result ).hasNoCause().hasMessageContaining( "ADMINS" ).hasMessageContaining( "INVALID_USER_ID" );
    }

    /**
     * Verifies that a failed removal carries the save failure message and its cause, without a form.
     */
    @Test
    void shouldCarrySaveFailedAndCause_whenFileOperationFails() {
        // GIVEN
        IOException cause = new IOException( "disk full" );

        // WHEN
        PlayerListUpdateException result = PlayerListUpdateException.saveFailed( PlayerList.BANNED, null, cause );

        // THEN
        assertThat( result.getMessageKey() ).isEqualTo( PlayerListMessage.SAVE_FAILED );
        assertThat( result.getSubmittedForm() ).isNull();
        assertThat( result.isFailure() ).isTrue();
        assertThat( result ).hasCause( cause );
    }

    /**
     * Verifies that the required arguments of both factories are rejected when missing.
     */
    @Test
    void shouldThrowException_whenRequiredArgumentIsNull() {
        // GIVEN
        IOException cause = new IOException( "disk full" );

        // WHEN
        Throwable message = catchThrowable( () -> PlayerListUpdateException.rejected( null, PlayerList.BANNED, null ) );
        Throwable list = catchThrowable(
                () -> PlayerListUpdateException.rejected( PlayerListMessage.NOT_LISTED, null, null ) );
        Throwable failedList = catchThrowable( () -> PlayerListUpdateException.saveFailed( null, null, cause ) );
        Throwable failedCause =
                catchThrowable( () -> PlayerListUpdateException.saveFailed( PlayerList.BANNED, null, null ) );

        // THEN
        assertThat( message ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "messageKey" );
        assertThat( list ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerList" );
        assertThat( failedList ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerList" );
        assertThat( failedCause ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "cause" );
    }
}
