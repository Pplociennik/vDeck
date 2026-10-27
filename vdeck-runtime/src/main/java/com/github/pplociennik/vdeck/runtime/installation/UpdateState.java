package com.github.pplociennik.vdeck.runtime.installation;

/**
 * Transaction state stored in the header of {@code apply.journal} (AD-024).
 *
 * <pre>
 * PREPARED -> APPLYING -> COMMITTED
 *     |           |
 *     +-----------+--> ROLLING_BACK -> ROLLED_BACK
 * </pre>
 *
 * <p>A non-terminal state found at start means an interrupted transaction, which recovery always rolls
 * back instead of retrying the update.
 */
public enum UpdateState {

    /**
     * The journal lists every operation; nothing in the installation has changed yet.
     */
    PREPARED,

    /**
     * Operations are being applied; each one records its own intent and completion.
     */
    APPLYING,

    /**
     * The complete new set has been validated; the new version may start.
     */
    COMMITTED,

    /**
     * Operations are being undone in reverse order.
     */
    ROLLING_BACK,

    /**
     * Every original has been restored; the previous version may start.
     */
    ROLLED_BACK;

    /**
     * Reports whether the transaction has ended and its leftovers may be cleaned up.
     *
     * @return {@code true} for {@link #COMMITTED} and {@link #ROLLED_BACK}
     */
    public boolean isTerminal() {
        return this == COMMITTED || this == ROLLED_BACK;
    }
}
