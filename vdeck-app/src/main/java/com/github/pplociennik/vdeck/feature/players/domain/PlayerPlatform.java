package com.github.pplociennik.vdeck.feature.players.domain;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Platform a player joins the server from. With crossplay enabled the Valheim server identifies a player
 * by the platform name, an underscore and the platform's user id, for example
 * {@code Steam_76561198012345678}, so every entry the panel writes carries that prefix.
 *
 * <p>Each platform accepts only user ids in its own format, which keeps a mistyped id from being written
 * to a list where it would never match a player.
 */
public enum PlayerPlatform {

    /**
     * Steam; the user id is the 17-digit SteamID64, which always begins with {@code 7656119}.
     */
    STEAM( "Steam", "7656119\\d{10}" ),

    /**
     * Xbox, including Microsoft Store and Game Pass for PC; the user id is the numeric Xbox user id (XUID),
     * a 64-bit number of at most 20 digits.
     */
    XBOX( "Xbox", "\\d{1,20}" );

    /**
     * Separator between the platform prefix and the user id in a list entry.
     */
    private static final String SEPARATOR = "_";

    private final String prefix;

    private final Pattern userIdPattern;

    PlayerPlatform( @Nonnull String prefix, @Nonnull String userIdPattern ) {
        this.prefix = requireNonNull( prefix, "prefix must not be null" );
        this.userIdPattern = Pattern.compile( requireNonNull( userIdPattern, "userIdPattern must not be null" ) );
    }

    /**
     * Returns the name the Valheim server writes before the user id of this platform.
     *
     * @return platform prefix without the separator, for example {@code Steam}
     */
    public @Nonnull String getPrefix() {
        return prefix;
    }

    /**
     * Reports whether a user id has the format of this platform.
     *
     * @param userId
     *         user id without the platform prefix
     * @return {@code true} when the whole id matches the platform's format
     * @throws NullPointerException
     *         if the id is null
     */
    public boolean accepts( @Nonnull String userId ) {
        requireNonNull( userId, "userId must not be null" );
        return userIdPattern.matcher( userId ).matches();
    }

    /**
     * Builds the list entry the Valheim server matches for a player of this platform.
     *
     * @param userId
     *         user id without the platform prefix
     * @return entry made of the prefix, the separator and the id, for example {@code Steam_76561198012345678}
     * @throws NullPointerException
     *         if the id is null
     */
    public @Nonnull String identifierOf( @Nonnull String userId ) {
        requireNonNull( userId, "userId must not be null" );
        return prefix + SEPARATOR + userId;
    }

    /**
     * Returns the part of a list entry following this platform's prefix and separator.
     *
     * @param identifier
     *         list entry, for example {@code Steam_76561198012345678}
     * @return user id after the prefix, or empty when the entry does not begin with this platform's prefix
     *         and separator
     * @throws NullPointerException
     *         if the entry is null
     */
    public @Nonnull Optional< String > userIdOf( @Nonnull String identifier ) {
        requireNonNull( identifier, "identifier must not be null" );
        String start = prefix + SEPARATOR;
        return identifier.startsWith( start ) ? Optional.of( identifier.substring( start.length() ) ) : Optional.empty();
    }
}
