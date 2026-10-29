package com.github.pplociennik.vdeck.feature.server;

import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static java.util.Objects.requireNonNull;

/**
 * Serves the "Stan serwera" screen opened from the top of the side menu: a condensed, read-only summary of
 * the managed process and of the stored launch profile, with links to the panels that change each part.
 *
 * <p>The screen only presents what the panel already holds - the runtime snapshot contributed by
 * {@link ServerStatusAdvice} and the cached, secret-free {@code LaunchProfile} - so opening it never touches
 * the game server, the disk or the network. The server password is shown only as whether it is set.
 */
@Controller
@Log4j2
class ServerStateController {

    /**
     * Model attribute holding the secret-free launch profile summarised by the screen.
     */
    static final String LAUNCH_PROFILE_ATTRIBUTE = "launchProfile";

    /**
     * Model attribute holding the local creation time of the managed process; absent without a process.
     */
    static final String STARTED_AT_ATTRIBUTE = "startedAt";

    /**
     * Model attribute holding how long the managed process has existed; absent without a process.
     */
    static final String UPTIME_ATTRIBUTE = "uptime";

    /**
     * Model attribute holding the file name of the latest launch's log; absent before the first launch.
     * Only the name is shown, so the summary never reveals the absolute installation path.
     */
    static final String LOG_FILE_NAME_ATTRIBUTE = "logFileName";

    private static final String SERVER_STATE_VIEW = "server/server-state";

    private final LaunchProfileService launchProfileService;

    private final Clock clock;

    /**
     * Creates the controller showing process times in the system time zone.
     *
     * @param launchProfileService
     *         source of the stored launch profile
     * @throws NullPointerException
     *         if the service is null
     */
    @Autowired
    ServerStateController( @Nonnull LaunchProfileService launchProfileService ) {
        this( launchProfileService, Clock.systemDefaultZone() );
    }

    /**
     * Creates the controller with an explicit clock, whose zone converts the process creation time.
     *
     * @param launchProfileService
     *         source of the stored launch profile
     * @param clock
     *         clock whose zone is used for displayed times
     * @throws NullPointerException
     *         if any argument is null
     */
    ServerStateController( @Nonnull LaunchProfileService launchProfileService, @Nonnull Clock clock ) {
        this.launchProfileService = requireNonNull( launchProfileService, "launchProfileService must not be null" );
        this.clock = requireNonNull( clock, "clock must not be null" );
    }

    /**
     * Displays the server state summary. The status is the snapshot {@link ServerStatusAdvice} has already
     * put into the model, so the screen and the island above it describe the same moment.
     *
     * @param serverStatus
     *         current runtime snapshot taken from the model, never bound from request parameters
     * @param model
     *         model receiving the launch profile, the log file name and, with a managed process, its creation
     *         time and uptime
     * @return server state template name
     * @throws NullPointerException
     *         if any argument is null
     */
    @GetMapping( "/server-state" )
    @Nonnull
    String showServerState(
            @ModelAttribute( name = ServerStatusAdvice.SERVER_STATUS_ATTRIBUTE, binding = false )
            @Nonnull ServerStatus serverStatus,
            @Nonnull Model model ) {
        requireNonNull( serverStatus, "serverStatus must not be null" );
        requireNonNull( model, "model must not be null" );

        log.debug( "Showing server state summary in state {}", serverStatus.getState() );
        model.addAttribute( LAUNCH_PROFILE_ATTRIBUTE, launchProfileService.getProfile() );
        Instant startedAt = serverStatus.getStartedAt();
        if ( startedAt != null ) {
            model.addAttribute( STARTED_AT_ATTRIBUTE, LocalDateTime.ofInstant( startedAt, clock.getZone() ) );
            model.addAttribute( UPTIME_ATTRIBUTE, uptimeSince( startedAt ) );
        }
        Path logFile = serverStatus.getLogFile();
        if ( logFile != null && logFile.getFileName() != null ) {
            model.addAttribute( LOG_FILE_NAME_ATTRIBUTE, logFile.getFileName().toString() );
        }
        return SERVER_STATE_VIEW;
    }

    /**
     * Measures how long the process has existed. A creation time ahead of the clock - possible after the
     * system clock was set back - gives zero rather than a negative duration.
     */
    private Duration uptimeSince( Instant startedAt ) {
        Duration uptime = Duration.between( startedAt, clock.instant() );
        return uptime.isNegative() ? Duration.ZERO : uptime;
    }
}
