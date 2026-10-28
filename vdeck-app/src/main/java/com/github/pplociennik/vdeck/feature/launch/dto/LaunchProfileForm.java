package com.github.pplociennik.vdeck.feature.launch.dto;

import jakarta.annotation.Nonnull;

/**
 * Form of one launch profile panel. Every panel stores only its own settings, and a rejected form is
 * shown again on its panel, so the launch profile update exception carries it in the form returned by
 * {@link #withoutSecrets()}.
 */
public sealed interface LaunchProfileForm
        permits ServerIdentityForm, ServerAccessForm, WorldSaveForm, ServerBackupsForm, GameSettingsForm {

    /**
     * Returns the form that may be shown again and kept in the session: a form holding a secret returns a
     * copy without it, every other form returns itself.
     *
     * @return this form without any submitted secret
     */
    default @Nonnull LaunchProfileForm withoutSecrets() {
        return this;
    }
}
