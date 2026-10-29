package com.github.pplociennik.vdeck.feature.players.dto;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the empty form of {@link PlayerEntryForm} shown before anything is submitted.
 */
class PlayerEntryFormTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the empty form preselects Steam and holds no user id.
     */
    @Test
    void shouldChooseSteamWithoutUserId_whenFormIsEmpty() {
        // GIVEN
        // nothing submitted yet

        // WHEN
        PlayerEntryForm result = PlayerEntryForm.empty();

        // THEN
        assertThat( result ).isEqualTo( new PlayerEntryForm( PlayerPlatform.STEAM, null ) );
    }
}
