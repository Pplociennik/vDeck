package com.github.pplociennik.vdeck.feature.launch.service;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerBackupsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerIdentityForm;
import com.github.pplociennik.vdeck.feature.launch.dto.WorldSaveForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import jakarta.annotation.Nonnull;

/**
 * Defines reading and updating the Valheim server launch profile stored in
 * {@code valheim-server.properties}. The profile is edited on several panels, so each panel updates only
 * the settings it shows and leaves the others unchanged.
 */
public interface LaunchProfileService {

    /**
     * Publishes a typed configuration from the already loaded properties, without rereading disk.
     * Called at startup after file loading; a successful profile update publishes its new configuration
     * from the values it has just stored instead. Malformed typed values use PropertyReader fallbacks;
     * server readiness is checked only when starting the server.
     */
    void reload();

    /**
     * Returns one immutable configuration for both validation and command generation.
     * This value contains the password and must never be passed to a web model or serialized.
     *
     * @return current launch configuration, replaced only after successful updates
     * @throws IllegalStateException
     *         if startup has not initialized the configuration
     */
    @Nonnull
    LaunchConfiguration getConfiguration();

    /**
     * Returns the cached, secret-free launch profile. Values are interpreted during publication,
     * using defaults for malformed typed settings. Manual file edits take effect after panel restart.
     *
     * @return typed launch profile; the password is represented only by whether it is set
     * @throws IllegalStateException
     *         if startup has not initialized the configuration
     */
    @Nonnull
    LaunchProfile getProfile();

    /**
     * Validates the submitted server identity and stores it together. The stored password may not be a
     * part of the new server name. Nothing is stored when any check fails; other settings are never
     * changed.
     *
     * @param form
     *         submitted server identity that has already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateServerIdentity( @Nonnull ServerIdentityForm form );

    /**
     * Validates and stores the server password: a new one, the stored one kept when the field is empty,
     * or none when removal was requested. The password may not be a part of the stored server name.
     * Nothing is stored when the check fails; other settings are never changed.
     *
     * @param form
     *         submitted access settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the password or the settings file cannot be written; nothing was stored
     */
    void updateServerAccess( @Nonnull ServerAccessForm form );

    /**
     * Validates the submitted world and save settings and stores them together. The save directory must
     * stay inside the installation directory. Nothing is stored when any check fails; other settings are
     * never changed.
     *
     * @param form
     *         submitted world and save settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateWorldSave( @Nonnull WorldSaveForm form );

    /**
     * Validates the submitted automatic backup settings and stores them together. Nothing is stored when
     * any check fails; other settings are never changed.
     *
     * @param form
     *         submitted backup settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateServerBackups( @Nonnull ServerBackupsForm form );

    /**
     * Validates the submitted game settings and stores them together. Nothing is stored when any check
     * fails; other settings are never changed.
     *
     * @param form
     *         submitted game settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateGameSettings( @Nonnull GameSettingsForm form );
}
