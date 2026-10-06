package com.github.pplociennik.vdeck.feature.launch.domain;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Typed view of the stored Valheim server launch profile. The server password is represented only by
 * whether it is set, so this value can be handed to the web layer without exposing the secret.
 *
 * @param serverName
 *         server name shown in the server list
 * @param port
 *         first of the two consecutive ports used by the server
 * @param worldName
 *         name of the world created or loaded by the server
 * @param passwordSet
 *         whether a server password is configured
 * @param publicServer
 *         whether the server is listed in the server browser
 * @param crossplay
 *         whether the server runs on the crossplay backend
 * @param instanceId
 *         crossplay instance identifier; empty when unused
 * @param saveDirectory
 *         directory of worlds and player lists, relative to the installation directory
 * @param saveInterval
 *         interval between automatic world saves, in seconds
 * @param backupCount
 *         number of automatic backups kept by the server
 * @param backupShort
 *         interval before the first automatic backup, in seconds
 * @param backupLong
 *         interval between the subsequent automatic backups, in seconds
 * @param preset
 *         world modifier preset, {@code null} when none is applied
 * @param modifiers
 *         values of the modifiers that are set; a modifier missing from the map keeps the game default
 * @param enabledKeys
 *         world modifier keys that are enabled
 */
public record LaunchProfile(
        @Nonnull String serverName,
        int port,
        @Nonnull String worldName,
        boolean passwordSet,
        boolean publicServer,
        boolean crossplay,
        @Nonnull String instanceId,
        @Nonnull String saveDirectory,
        int saveInterval,
        int backupCount,
        int backupShort,
        int backupLong,
        @Nullable WorldPreset preset,
        @Nonnull Map< WorldModifier, String > modifiers,
        @Nonnull Set< WorldKey > enabledKeys ) {

    /**
     * Creates a profile and freezes its collections.
     *
     * @throws NullPointerException
     *         if a component other than {@code preset} is {@code null}
     */
    public LaunchProfile {
        requireNonNull( serverName, "serverName must not be null" );
        requireNonNull( worldName, "worldName must not be null" );
        requireNonNull( instanceId, "instanceId must not be null" );
        requireNonNull( saveDirectory, "saveDirectory must not be null" );
        modifiers = Map.copyOf( requireNonNull( modifiers, "modifiers must not be null" ) );
        enabledKeys = Set.copyOf( requireNonNull( enabledKeys, "enabledKeys must not be null" ) );
    }

    /**
     * Mirrors the Valheim server's own rule: it refuses to start when the server name contains the
     * password. The comparison ignores letter case, so the panel is never more permissive than the
     * server.
     *
     * <p>The rule lives here rather than in one of the services because both of them need it — the
     * settings screen rejects such a pair when it is saved, and the start refuses to launch a profile
     * that was written into the file by hand.
     *
     * @param password
     *         server password; an empty password is never part of a name
     * @param serverName
     *         server name shown in the server list
     * @return {@code true} when the server would refuse this combination
     * @throws NullPointerException
     *         if either argument is {@code null}
     */
    public static boolean isPasswordPartOfName( @Nonnull String password, @Nonnull String serverName ) {
        requireNonNull( password, "password must not be null" );
        requireNonNull( serverName, "serverName must not be null" );
        return !password.isEmpty()
                && serverName.toLowerCase( Locale.ROOT ).contains( password.toLowerCase( Locale.ROOT ) );
    }
}