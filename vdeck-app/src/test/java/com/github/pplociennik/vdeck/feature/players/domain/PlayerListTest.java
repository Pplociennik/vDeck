package com.github.pplociennik.vdeck.feature.players.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PlayerList} names the files the Valheim server reads and maps address segments to
 * lists.
 */
class PlayerListTest {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that every list uses the file name the Valheim server reads.
     */
    @Test
    void shouldUseValheimFileNames_whenListsAreRead() {
        // GIVEN
        // the three lists of the Valheim server

        // WHEN
        String banned = PlayerList.BANNED.getFileName();
        String admins = PlayerList.ADMINS.getFileName();
        String permitted = PlayerList.PERMITTED.getFileName();

        // THEN
        assertThat( banned ).isEqualTo( "bannedlist.txt" );
        assertThat( admins ).isEqualTo( "adminlist.txt" );
        assertThat( permitted ).isEqualTo( "permittedlist.txt" );
    }

    /**
     * Verifies that every list is found again by its own address segment and that its panel address ends
     * with that segment.
     *
     * @param list
     *         list being looked up
     */
    @ParameterizedTest( name = "[{index}] list={0}" )
    @EnumSource( PlayerList.class )
    void shouldReturnList_whenPathSegmentIsItsOwn( PlayerList list ) {
        // GIVEN
        String segment = list.getPathSegment();

        // WHEN
        PlayerList result = PlayerList.fromPathSegment( segment );

        // THEN
        assertThat( result ).isEqualTo( list );
        assertThat( list.getPath() ).isEqualTo( "/players/" + segment );
    }

    /**
     * Verifies that a segment naming no list, including a differently written one, is refused.
     *
     * @param segment
     *         segment naming no list
     */
    @ParameterizedTest( name = "[{index}] segment=\"{0}\"" )
    @ValueSource( strings = { "", "BANS", "bannedlist.txt", "other" } )
    void shouldThrowException_whenPathSegmentIsUnknown( String segment ) {
        // GIVEN
        // a segment that no list carries

        // WHEN
        Throwable thrown = catchThrowable( () -> PlayerList.fromPathSegment( segment ) );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalArgumentException.class ).hasMessageContaining( "Unknown player list" );
    }

    /**
     * Verifies that a missing segment is rejected.
     */
    @Test
    void shouldThrowException_whenPathSegmentIsNull() {
        // GIVEN
        // no segment

        // WHEN
        Throwable thrown = catchThrowable( () -> PlayerList.fromPathSegment( null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "pathSegment" );
    }
}
