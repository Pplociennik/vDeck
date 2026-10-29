package com.github.pplociennik.vdeck.feature.players.domain;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * One player on a list, read from a line of the list file.
 *
 * <p>An entry whose prefix names a known platform is split into the platform and the user id. Any other
 * entry - one written without a prefix or by another tool - keeps no platform and uses the whole line as
 * its user id, so the panel still shows it and can remove it.
 *
 * @param identifier
 *         the whole entry exactly as the server matches it, for example {@code Steam_76561198012345678}
 * @param platform
 *         platform named by the entry's prefix, or {@code null} when the prefix names no known platform
 * @param userId
 *         user id after the prefix, or the whole entry when there is no known platform
 */
public record PlayerEntry( @Nonnull String identifier, @Nullable PlayerPlatform platform, @Nonnull String userId ) {

    /**
     * Beginning of a comment line; the Valheim server skips such lines and so does the panel.
     */
    private static final String COMMENT_PREFIX = "//";

    /**
     * Creates the entry and checks that its parts are present.
     *
     * @throws NullPointerException
     *         if the identifier or the user id is null
     */
    public PlayerEntry {
        requireNonNull( identifier, "identifier must not be null" );
        requireNonNull( userId, "userId must not be null" );
    }

    /**
     * Reads the entry held by one line of a list file.
     *
     * @param line
     *         line of the file, possibly with surrounding spaces
     * @return entry of the line, or empty for a blank line or a comment
     * @throws NullPointerException
     *         if the line is null
     */
    public static @Nonnull Optional< PlayerEntry > parse( @Nonnull String line ) {
        requireNonNull( line, "line must not be null" );
        String identifier = line.strip();
        if ( identifier.isEmpty() || identifier.startsWith( COMMENT_PREFIX ) ) {
            return Optional.empty();
        }
        for ( PlayerPlatform platform : PlayerPlatform.values() ) {
            Optional< String > userId = platform.userIdOf( identifier );
            if ( userId.isPresent() ) {
                return Optional.of( new PlayerEntry( identifier, platform, userId.get() ) );
            }
        }
        return Optional.of( new PlayerEntry( identifier, null, identifier ) );
    }

    /**
     * Creates the entry for a player of a known platform.
     *
     * @param platform
     *         platform the player joins from
     * @param userId
     *         user id without the platform prefix
     * @return entry with the identifier the server matches
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull PlayerEntry of( @Nonnull PlayerPlatform platform, @Nonnull String userId ) {
        requireNonNull( platform, "platform must not be null" );
        requireNonNull( userId, "userId must not be null" );
        return new PlayerEntry( platform.identifierOf( userId ), platform, userId );
    }
}
