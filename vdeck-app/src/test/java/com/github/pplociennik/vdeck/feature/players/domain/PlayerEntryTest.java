package com.github.pplociennik.vdeck.feature.players.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies how {@link PlayerEntry} reads the lines of a player list file and builds new entries.
 */
class PlayerEntryTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a Steam entry is split into its platform and user id, ignoring surrounding spaces.
     */
    @Test
    void shouldReadSteamEntry_whenLineHasSteamPrefix() {
        // GIVEN
        String line = "  Steam_76561198012345678 ";

        // WHEN
        var result = PlayerEntry.parse( line );

        // THEN
        assertThat( result ).contains(
                new PlayerEntry( "Steam_76561198012345678", PlayerPlatform.STEAM, "76561198012345678" ) );
    }

    /**
     * Verifies that an Xbox entry is split into its platform and user id.
     */
    @Test
    void shouldReadXboxEntry_whenLineHasXboxPrefix() {
        // GIVEN
        String line = "Xbox_2535412345678901";

        // WHEN
        var result = PlayerEntry.parse( line );

        // THEN
        assertThat( result ).contains( new PlayerEntry( "Xbox_2535412345678901", PlayerPlatform.XBOX, "2535412345678901" ) );
    }

    /**
     * Verifies that an entry without a known platform prefix is kept whole and has no platform.
     *
     * @param line
     *         entry without a known prefix
     */
    @ParameterizedTest( name = "[{index}] line=\"{0}\"" )
    @ValueSource( strings = { "76561198012345678", "PlayFab_ABCDEF", "steam_76561198012345678" } )
    void shouldKeepWholeEntryWithoutPlatform_whenPrefixIsUnknown( String line ) {
        // GIVEN
        // a line written without a known platform prefix

        // WHEN
        var result = PlayerEntry.parse( line );

        // THEN
        assertThat( result ).contains( new PlayerEntry( line, null, line ) );
    }

    /**
     * Verifies that blank lines and comments hold no entry.
     *
     * @param line
     *         blank line or comment
     */
    @ParameterizedTest( name = "[{index}] line=\"{0}\"" )
    @ValueSource( strings = { "", "   ", "// List banned players ID  ONE per line", "  //Steam_76561198012345678" } )
    void shouldReturnEmpty_whenLineIsBlankOrComment( String line ) {
        // GIVEN
        // a line the server skips

        // WHEN
        var result = PlayerEntry.parse( line );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a new entry carries the prefixed identifier the server matches.
     */
    @Test
    void shouldBuildPrefixedIdentifier_whenEntryIsCreatedForPlatform() {
        // GIVEN
        String userId = "76561198012345678";

        // WHEN
        PlayerEntry result = PlayerEntry.of( PlayerPlatform.STEAM, userId );

        // THEN
        assertThat( result ).isEqualTo( new PlayerEntry( "Steam_76561198012345678", PlayerPlatform.STEAM, userId ) );
    }

    /**
     * Verifies that missing arguments are rejected by the factories and the constructor.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // no line, platform, user id or identifier

        // WHEN
        Throwable parse = catchThrowable( () -> PlayerEntry.parse( null ) );
        Throwable platform = catchThrowable( () -> PlayerEntry.of( null, "1" ) );
        Throwable userId = catchThrowable( () -> PlayerEntry.of( PlayerPlatform.XBOX, null ) );
        Throwable identifier = catchThrowable( () -> new PlayerEntry( null, null, "1" ) );

        // THEN
        assertThat( parse ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "line" );
        assertThat( platform ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "platform" );
        assertThat( userId ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "userId" );
        assertThat( identifier ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "identifier" );
    }
}
