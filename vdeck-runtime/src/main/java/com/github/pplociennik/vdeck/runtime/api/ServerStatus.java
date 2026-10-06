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
 * <p>Which details are present follows from the state: a starting or running server carries its
 * process identifier, start instant and log file; a server that failed or crashed carries only the log
 * file, because that is where the administrator finds the reason; a stopped server carries nothing but
 * the state. The constructor is private and every combination is built by a named factory, so an
 * inconsistent snapshot — a {@code STOPPED} status holding a process identifier, for instance — cannot
 * be created by accident.
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

    private ServerStatus( @Nonnull ServerState state, @Nullable Long pid, @Nullable Instant startedAt,
                          @Nullable Path logFile ) {
        this.state = requireNonNull( state, "state must not be null" );
        this.pid = pid;
        this.startedAt = startedAt;
        this.logFile = logFile;
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
}