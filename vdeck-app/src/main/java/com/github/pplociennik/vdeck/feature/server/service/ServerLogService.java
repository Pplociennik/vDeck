package com.github.pplociennik.vdeck.feature.server.service;

import jakarta.annotation.Nonnull;

import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Decides where the Valheim server writes the log of a single launch, and keeps that directory from
 * growing without bound.
 *
 * <p>The panel does not capture the server's console output. It only chooses the path and passes it to
 * the server through {@code -logFile}, so the file belongs to the server process and survives the
 * panel ({@code design.md} §6).
 */
public interface ServerLogService {

    /**
     * Reserves the log file for a launch that is about to happen and makes sure its directory exists.
     *
     * <p>Each launch gets its own file, named after the moment it started, below a directory named
     * after the day:
     *
     * <pre>
     * &lt;game.log.directory&gt;/2026-09-28/2026-09-28_21-43-07.log
     * </pre>
     *
     * <p>Because the name is unique per launch, a restart never overwrites the log that would explain
     * why the previous run ended — which is also why the panel does not have to detect a log file being
     * replaced underneath it.
     *
     * <p>Old day directories are removed first, according to {@code game.log.retention.days}. A failure
     * to clean up does not prevent the launch: losing disk space is a smaller problem than refusing to
     * start the server. The file itself is not created; the server creates it when it starts.
     *
     * @return absolute path the server should be told to write its log to
     * @throws UncheckedIOException
     *         if the log directory cannot be created or written to
     */
    @Nonnull
    Path createLogFileForNewStart();
}