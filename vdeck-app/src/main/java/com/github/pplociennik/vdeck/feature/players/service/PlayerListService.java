package com.github.pplociennik.vdeck.feature.players.service;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerEntry;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Defines reading and changing the player lists the Valheim server keeps in its save directory
 * ({@code -savedir} of the launch profile): banned players, administrators and permitted players.
 *
 * <p>The service works on the files only. The server reads them when it starts, so a change takes effect
 * at the next server start, and a running server that writes a list itself - for example after an in-game
 * ban - may overwrite a change made meanwhile.
 */
public interface PlayerListService {

    /**
     * Returns the players on a list in the order of the file. A list whose file does not exist yet, which
     * is the case before the server's first start, is empty.
     *
     * @param playerList
     *         list to read
     * @return unmodifiable entries of the file, without blank lines and comments
     * @throws NullPointerException
     *         if the list is null
     * @throws java.io.UncheckedIOException
     *         if the file cannot be read or exceeds its size limit
     * @throws com.github.pplociennik.vdeck.shared.exc.PathResolutionException
     *         if the save directory of the launch profile leaves the installation directory
     */
    @Nonnull
    List< PlayerEntry > getEntries( @Nonnull PlayerList playerList );

    /**
     * Adds a player to the end of a list. The file keeps its other lines, comments included, and is
     * replaced atomically; it and the save directory are created when they do not exist yet.
     *
     * @param playerList
     *         list to extend
     * @param form
     *         platform and user id of the player
     * @throws NullPointerException
     *         if an argument is null
     * @throws PlayerListUpdateException
     *         if a field is missing, the user id does not fit the platform, the player is already listed or
     *         the file cannot be read or written; nothing was stored
     */
    void addPlayer( @Nonnull PlayerList playerList, @Nonnull PlayerEntryForm form );

    /**
     * Removes a player from a list. Every line holding exactly this entry is removed, the other lines are
     * kept and the file is replaced atomically.
     *
     * @param playerList
     *         list to shorten
     * @param identifier
     *         whole entry as shown on the list, for example {@code Steam_76561198012345678}
     * @throws NullPointerException
     *         if an argument is null
     * @throws PlayerListUpdateException
     *         if the entry is not on the list or the file cannot be read or written; nothing was stored
     */
    void removePlayer( @Nonnull PlayerList playerList, @Nonnull String identifier );
}
