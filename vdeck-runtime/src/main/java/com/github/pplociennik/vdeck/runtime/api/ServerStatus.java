package com.github.pplociennik.vdeck.runtime.api;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.nio.file.Path;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Immutable snapshot of what the runtime knows about the managed server at one moment. The web layer
 * renders this snapshot and never reaches into the runtime's internal state.
 *
 * <p>Live states retain process identity, launch correlation and the log path, including STOP_FAILED.
 * Failed starts and crashes retain only their log path. Recovery may offer a saved launch identifier;
 * a stopped server carries no process. Named factories prevent inconsistent combinations.
 *
 * <p>No component is a secret, so the generated {@code toString()} is safe to log.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class ServerStatus {

    /**
     * Lifecycle state the server is in; never {@code null}.
     */
    private final ServerState state;

    /**
     * Identifier of the managed process, or {@code null} when no process is being managed.
     */
    private final Long pid;

    /**
     * Moment the managed process was created, or {@code null} when no process is being managed.
     */
    private final Instant startedAt;

    /**
     * Log file of the most recent launch, kept after a failure so the administrator can read why the
     * server did not come up; {@code null} when nothing has been launched yet.
     */
    private final Path logFile;

    /** Opaque launch identifier, retained across failed stops and recovery; null without a launch. */
    private final String launchId;

    /** Safe, closed stop-failure category; null unless the last stop failed. */
    private final StopFailure stopFailure;

    private ServerStatus( @Nonnull ServerState state, @Nullable Long pid, @Nullable Instant startedAt,
                          @Nullable Path logFile ) {
        this( state, pid, startedAt, logFile, null, null );
    }

    private ServerStatus( ServerState state, Long pid, Instant startedAt, Path logFile,
                          String launchId, StopFailure stopFailure ) {
        this.state = requireNonNull( state, "state must not be null" );
        this.pid = pid;
        this.startedAt = startedAt;
        this.logFile = logFile;
        this.launchId = launchId;
        this.stopFailure = stopFailure;
    }

    /**
     * Creates a starting launch with an opaque form-correlation identifier.
     *
     * @param server
     *         managed process
     * @param launchId
     *         immutable launch identifier
     * @return correlated starting snapshot
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull ServerStatus starting( @Nonnull RunningServer server, @Nonnull String launchId ) {
        return managed( ServerState.STARTING, server, launchId, null );
    }

    /**
     * Creates a running launch snapshot.
     *
     * @param server
     *         managed process
     * @param launchId
     *         immutable launch identifier
     * @return correlated running snapshot
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull ServerStatus running( @Nonnull RunningServer server, @Nonnull String launchId ) {
        return managed( ServerState.RUNNING, server, launchId, null );
    }

    /**
     * Creates a stopping snapshot retaining the launch's process details.
     *
     * @param server
     *         process whose exit has not yet been confirmed
     * @param launchId
     *         immutable launch identifier
     * @return stopping snapshot
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull ServerStatus stopping( @Nonnull RunningServer server, @Nonnull String launchId ) {
        return managed( ServerState.STOPPING, server, launchId, null );
    }

    /**
     * Creates a failed-stop snapshot without discarding a still-live process.
     *
     * @param server
     *         retained managed process
     * @param launchId
     *         immutable launch identifier
     * @param failure
     *         safe reason for failure
     * @return failed-stop snapshot
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull ServerStatus stopFailed( @Nonnull RunningServer server, @Nonnull String launchId,
                                                   @Nonnull StopFailure failure ) {
        return managed( ServerState.STOP_FAILED, server, launchId,
                requireNonNull( failure, "failure must not be null" ) );
    }

    /**
     * Creates a snapshot blocking launch while a leftover process needs an administrator's decision.
     *
     * @param launchId
     *         saved launch that can be explicitly adopted, or null for an external/unverified process
     * @return recovery-required snapshot
     */
    public static @Nonnull ServerStatus recoveryRequired( @Nullable String launchId ) {
        return new ServerStatus( ServerState.RECOVERY_REQUIRED, null, null, null, launchId, null );
    }

    /**
     * Returns the status of a panel that manages no server process.
     *
     * @return status in state {@link ServerState#STOPPED}, without any process details
     */
    public static @Nonnull ServerStatus stopped() {
        return new ServerStatus( ServerState.STOPPED, null, null, null );
    }

    /**
     * Returns the status of a process that has just been created and is not yet confirmed to run.
     *
     * @param pid
     *         identifier of the created process
     * @param startedAt
     *         moment the process was created
     * @param logFile
     *         log file this launch writes to
     * @return status in state {@link ServerState#STARTING}
     * @throws NullPointerException
     *         if {@code startedAt} or {@code logFile} is {@code null}
     */
    public static @Nonnull ServerStatus starting( long pid, @Nonnull Instant startedAt, @Nonnull Path logFile ) {
        return new ServerStatus( ServerState.STARTING, pid,
                requireNonNull( startedAt, "startedAt must not be null" ),
                requireNonNull( logFile, "logFile must not be null" ) );
    }

    /**
     * Returns the status of a process confirmed to be running.
     *
     * @param pid
     *         identifier of the managed process
     * @param startedAt
     *         moment the process was created
     * @param logFile
     *         log file this launch writes to
     * @return status in state {@link ServerState#RUNNING}
     * @throws NullPointerException
     *         if {@code startedAt} or {@code logFile} is {@code null}
     */
    public static @Nonnull ServerStatus running( long pid, @Nonnull Instant startedAt, @Nonnull Path logFile ) {
        return new ServerStatus( ServerState.RUNNING, pid,
                requireNonNull( startedAt, "startedAt must not be null" ),
                requireNonNull( logFile, "logFile must not be null" ) );
    }

    /**
     * Returns the status of a launch whose process exited before the server became usable.
     *
     * @param logFile
     *         log file the failed launch wrote to, or {@code null} when it was never created
     * @return status in state {@link ServerState#START_FAILED}
     */
    public static @Nonnull ServerStatus startFailed( @Nullable Path logFile ) {
        return new ServerStatus( ServerState.START_FAILED, null, null, logFile );
    }

    /**
     * Returns the status of a server that exited on its own while it was running.
     *
     * @param logFile
     *         log file the crashed server wrote to, or {@code null} when it was never created
     * @return status in state {@link ServerState#CRASHED}
     */
    public static @Nonnull ServerStatus crashed( @Nullable Path logFile ) {
        return new ServerStatus( ServerState.CRASHED, null, null, logFile );
    }

    private static ServerStatus managed( ServerState state, RunningServer server, String launchId,
                                         StopFailure failure ) {
        requireNonNull( server, "server must not be null" );
        requireNonNull( launchId, "launchId must not be null" );
        return new ServerStatus( state, server.handle().pid(), server.startedAt(), server.logFile(),
                launchId, failure );
    }
}
