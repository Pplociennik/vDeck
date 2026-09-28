package com.github.pplociennik.vdeck.feature.server.service;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import jakarta.annotation.Nonnull;

import java.nio.file.Path;

/**
 * Turns the stored launch profile into the command that actually starts the Valheim server.
 *
 * <p>This is where the profile's settings become {@code valheim_server.exe} options. Three things do
 * not come from the profile and are added here:
 *
 * <ul>
 *   <li>{@code -nographics -batchmode}, which every headless server needs and no administrator should
 *       have to know about;</li>
 *   <li>{@code -logFile}, which the panel decides so that each launch gets its own log;</li>
 *   <li>{@code SteamAppId} in the child's environment, read from the publisher's start script so a
 *       change to it does not require a new panel release.</li>
 * </ul>
 */
public interface LaunchCommandService {

    /**
     * Builds the launch from the captured configuration and the new log path.
     *
     * <p>An optional setting left empty means the option is not passed at all, which is how the profile
     * expresses "let the server decide". Reads only the panel settings and the publisher's script;
     * all profile values, including the password, come from the supplied configuration.
     *
     * @param configuration
     *         complete configuration captured for this launch
     * @param logFile
     *         absolute path this launch should write its log to
     * @return the immutable specification to execute
     * @throws NullPointerException
     *         if {@code configuration} or {@code logFile} is {@code null}
     */
    @Nonnull
    LaunchSpec build( @Nonnull LaunchConfiguration configuration, @Nonnull Path logFile );

    /**
     * Renders the mirror from the exact specification sent to the runtime, replacing the password
     * argument with an environment variable reference. Does not read settings or write files.
     *
     * @param launchSpec
     *         specification produced by {@link #build}
     * @return mirror script without the password argument's value
     * @throws NullPointerException
     *         if the specification is null
     */
    @Nonnull
    String renderScript( @Nonnull LaunchSpec launchSpec );
}
