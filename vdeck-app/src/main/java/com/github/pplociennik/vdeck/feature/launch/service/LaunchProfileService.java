package com.github.pplociennik.vdeck.feature.launch.service;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import jakarta.annotation.Nonnull;

/**
 * Defines reading and updating the Valheim server launch profile stored in
 * {@code valheim-server.properties}. The profile is edited on two screens, so each screen updates only
 * the settings it shows and leaves the others unchanged.
 */
public interface LaunchProfileService {

    /**
     * Returns the stored launch profile. A value that no longer matches its setting, for example after a
     * manual edit of the file, is replaced by the setting's default in the returned profile.
     *
     * @return typed launch profile; the password is represented only by whether it is set
     */
    @Nonnull
    LaunchProfile getProfile();

    /**
     * Validates the submitted server settings and stores them together. Nothing is stored when any check
     * fails; the game settings are never changed.
     *
     * @param form
     *         submitted server settings that have already passed annotation validation
     * @return successful result, or a rejection carrying the message to show
     */
    @Nonnull
    LaunchProfileUpdateResult updateServerSettings( @Nonnull ServerSettingsForm form );

    /**
     * Validates the submitted game settings and stores them together. Nothing is stored when any check
     * fails; the server settings are never changed.
     *
     * @param form
     *         submitted game settings that have already passed annotation validation
     * @return successful result, or a rejection carrying the message to show
     */
    @Nonnull
    LaunchProfileUpdateResult updateGameSettings( @Nonnull GameSettingsForm form );
}
