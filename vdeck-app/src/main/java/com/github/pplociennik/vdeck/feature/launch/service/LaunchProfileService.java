package com.github.pplociennik.vdeck.feature.launch.service;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import jakarta.annotation.Nonnull;

/**
 * Defines reading and updating the Valheim server launch profile stored in
 * {@code valheim-server.properties}. The profile is edited on two screens, so each screen updates only
 * the settings it shows and leaves the others unchanged.
 */
public interface LaunchProfileService {

    /**
     * Publishes a typed configuration from the already loaded properties, without rereading disk.
     * Called at startup after file loading and after successful profile updates. Malformed typed
     * values use PropertyReader fallbacks; server readiness is checked only when starting the server.
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
     * Validates the submitted server settings and stores them together. Nothing is stored when any check
     * fails; the game settings are never changed.
     *
     * @param form
     *         submitted server settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateServerSettings( @Nonnull ServerSettingsForm form );

    /**
     * Validates the submitted game settings and stores them together. Nothing is stored when any check
     * fails; the server settings are never changed.
     *
     * @param form
     *         submitted game settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    void updateGameSettings( @Nonnull GameSettingsForm form );
}
