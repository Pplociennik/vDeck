package com.github.pplociennik.vdeck.feature.players.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the user id formats and list entries of {@link PlayerPlatform}.
 */
class PlayerPlatformTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a 17-digit SteamID64 is accepted for Steam.
     */
    @Test
    void shouldAcceptSteamId_whenIdIsSteamId64() {
        // GIVEN
        String userId = "76561198012345678";

        // WHEN
        boolean result = PlayerPlatform.STEAM.accepts( userId );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that Steam refuses an id of the wrong length, of the wrong range or with other characters.
     *
     * @param userId
     *         id that is no SteamID64
     */
    @ParameterizedTest( name = "[{index}] userId=\"{0}\"" )
    @ValueSource( strings = { "", "7656119801234567", "765611980123456789", "12345678901234567", "7656119801234567a",
            " 76561198012345678" } )
    void shouldRejectSteamId_whenIdIsNoSteamId64( String userId ) {
        // GIVEN
        // an id outside the SteamID64 format

        // WHEN
        boolean result = PlayerPlatform.STEAM.accepts( userId );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that Xbox accepts numeric ids from one to twenty digits.
     *
     * @param userId
     *         numeric Xbox user id
     */
    @ParameterizedTest( name = "[{index}] userId=\"{0}\"" )
    @ValueSource( strings = { "1", "2535412345678901", "18446744073709551615" } )
    void shouldAcceptXboxId_whenIdHasUpToTwentyDigits( String userId ) {
        // GIVEN
        // a numeric id within the length of an XUID

        // WHEN
        boolean result = PlayerPlatform.XBOX.accepts( userId );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that Xbox refuses an empty id, an id longer than twenty digits and one with other
     * characters.
     *
     * @param userId
     *         id that is no XUID
     */
    @ParameterizedTest( name = "[{index}] userId=\"{0}\"" )
    @ValueSource( strings = { "", "123456789012345678901", "25354a" } )
    void shouldRejectXboxId_whenIdIsNoXuid( String userId ) {
        // GIVEN
        // an id outside the XUID format

        // WHEN
        boolean result = PlayerPlatform.XBOX.accepts( userId );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that the entry of a player is the platform prefix, an underscore and the user id.
     */
    @Test
    void shouldPrefixUserId_whenIdentifierIsBuilt() {
        // GIVEN
        String userId = "2535412345678901";

        // WHEN
        String result = PlayerPlatform.XBOX.identifierOf( userId );

        // THEN
        assertThat( result ).isEqualTo( "Xbox_2535412345678901" );
    }

    /**
     * Verifies that the user id is taken from an entry beginning with the platform's prefix.
     */
    @Test
    void shouldReturnUserId_whenIdentifierBeginsWithPrefix() {
        // GIVEN
        String identifier = "Steam_76561198012345678";

        // WHEN
        var result = PlayerPlatform.STEAM.userIdOf( identifier );

        // THEN
        assertThat( result ).contains( "76561198012345678" );
    }

    /**
     * Verifies that an entry of another platform, a bare id or a differently written prefix yields no user
     * id.
     *
     * @param identifier
     *         entry not beginning with the Steam prefix and separator
     */
    @ParameterizedTest( name = "[{index}] identifier=\"{0}\"" )
    @ValueSource( strings = { "Xbox_2535412345678901", "76561198012345678", "steam_76561198012345678",
            "Steam76561198012345678" } )
    void shouldReturnEmpty_whenIdentifierHasNoSteamPrefix( String identifier ) {
        // GIVEN
        // an entry of a different form

        // WHEN
        var result = PlayerPlatform.STEAM.userIdOf( identifier );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that missing ids and entries are rejected by every operation.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        PlayerPlatform platform = PlayerPlatform.STEAM;

        // WHEN
        Throwable accepts = catchThrowable( () -> platform.accepts( null ) );
        Throwable identifierOf = catchThrowable( () -> platform.identifierOf( null ) );
        Throwable userIdOf = catchThrowable( () -> platform.userIdOf( null ) );

        // THEN
        assertThat( accepts ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "userId" );
        assertThat( identifierOf ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "userId" );
        assertThat( userIdOf ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "identifier" );
    }
}
