package com.github.pplociennik.vdeck.runtime.api;



/** Safe diagnostic categories exposed to the application without native messages, paths, or secrets. */
public enum StopFailure {
    /** Complete snapshot publication failed and the operation could not continue. */
    SNAPSHOT,
    /** Required source world files or their directory are missing; ordinary Stop cannot continue. */
    WORLD_SAVE_MISSING,
    /** Process metadata no longer confirms the launch identity. */
    IDENTITY,
    /** Both graceful attempts were ineffective or the helper failed. */
    GRACEFUL,
    /** The shared operation deadline expired without confirming exit. */
    DEADLINE,
    /** Explicitly authorized force was refused or did not produce an observed exit. */
    FORCE
}
