package com.github.pplociennik.vdeck.feature.players.dto;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Carries the player submitted from a player list panel to be added to the list. Annotations accept any
 * user id of digits; whether the id has the chosen platform's format is checked by the player list
 * service. The form holds no secret.
 *
 * @param platform
 *         platform the player joins from
 * @param userId
 *         user id without the platform prefix, of digits only
 */
public record PlayerEntryForm(
        @Nullable @NotNull PlayerPlatform platform,
        @Nullable @NotBlank @Size( max = 20 ) @Pattern( regexp = "\\d*" ) String userId ) {

    /**
     * Creates the empty form shown before anything is submitted, with Steam chosen because most players
     * join from it.
     *
     * @return form with Steam chosen and no user id
     */
    public static @Nonnull PlayerEntryForm empty() {
        return new PlayerEntryForm( PlayerPlatform.STEAM, null );
    }
}
