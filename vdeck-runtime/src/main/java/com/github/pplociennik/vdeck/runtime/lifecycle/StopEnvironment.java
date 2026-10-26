package com.github.pplociennik.vdeck.runtime.lifecycle;

import com.github.pplociennik.vdeck.runtime.spi.ConsoleControlSender;
import com.github.pplociennik.vdeck.runtime.spi.WorldSnapshotStore;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Explicit I/O and worker boundaries for a deterministic stop/recovery lifecycle.
 *
 * @param snapshots
 *         complete snapshot publication boundary
 * @param signals
 *         isolated helper boundary
 * @param store
 *         durable token and installation lock owned by the runtime
 * @param workers
 *         bounded executor owned by the runtime
 * @param processes
 *         fresh process stream for each recovery scan
 * @param lookup
 *         process lookup used for saved launch verification
 */
record StopEnvironment( WorldSnapshotStore snapshots, ConsoleControlSender signals, ManagedLaunchStore store,
                        ExecutorService workers, Supplier< Stream< ProcessHandle > > processes,
                        LongFunction< Optional< ProcessHandle > > lookup ) {
}
