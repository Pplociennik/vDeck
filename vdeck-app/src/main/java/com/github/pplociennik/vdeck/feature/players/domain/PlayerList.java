package com.github.pplociennik.vdeck.feature.players.domain;

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Player list kept by the Valheim server in its save directory ({@code -savedir}), one panel each. The
 * server reads the lists when it starts, so a change made by the panel takes effect at the next start.
 *
 * <p>The path segment names the list in the panel's addresses and translation keys; the file name is the
 * one the Valheim server reads.
 */
public enum PlayerList {

    /**
     * Banned players; the server refuses their connections.
     */
    BANNED( "bans", "bannedlist.txt" ),

    /**
     * Server administrators; they may use the admin commands of the in-game console.
     */
    ADMINS( "admins", "adminlist.txt" ),

    /**
     * Permitted players; when the list holds any entry, only the listed players may join.
     */
    PERMITTED( "permitted", "permittedlist.txt" );

    /**
     * Common beginning of the addresses of all player list panels.
     */
    private static final String PATH_PREFIX = "/players/";

    private final String pathSegment;

    private final String fileName;

    PlayerList( @Nonnull String pathSegment, @Nonnull String fileName ) {
        this.pathSegment = requireNonNull( pathSegment, "pathSegment must not be null" );
        this.fileName = requireNonNull( fileName, "fileName must not be null" );
    }

    /**
     * Returns the list named by an address segment.
     *
     * @param pathSegment
     *         last segment of the panel's address, for example {@code bans}
     * @return list named by the segment
     * @throws NullPointerException
     *         if the segment is null
     * @throws IllegalArgumentException
     *         if no list has this segment
     */
    public static @Nonnull PlayerList fromPathSegment( @Nonnull String pathSegment ) {
        requireNonNull( pathSegment, "pathSegment must not be null" );
        for ( PlayerList list : values() ) {
            if ( list.pathSegment.equals( pathSegment ) ) {
                return list;
            }
        }
        throw new IllegalArgumentException( "Unknown player list: " + pathSegment );
    }

    /**
     * Returns the segment naming this list in addresses and translation keys.
     *
     * @return lowercase segment, for example {@code bans}
     */
    public @Nonnull String getPathSegment() {
        return pathSegment;
    }

    /**
     * Returns the name of the file the Valheim server reads this list from.
     *
     * @return file name inside the save directory, for example {@code bannedlist.txt}
     */
    public @Nonnull String getFileName() {
        return fileName;
    }

    /**
     * Returns the address of this list's panel.
     *
     * @return absolute path within the application, for example {@code /players/bans}
     */
    public @Nonnull String getPath() {
        return PATH_PREFIX + pathSegment;
    }
}
