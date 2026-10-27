package com.github.pplociennik.vdeck.updater;

/**
 * Durable progress of one journaled operation. Every destructive file-system action is preceded by a
 * persisted intent, so recovery knows which actions may have happened and inspects the files to learn
 * whether they did.
 *
 * <pre>
 * PENDING -> BACKUP_INTENT -> BACKED_UP -> INSTALL_INTENT -> APPLIED
 *     any step after PENDING -> RESTORE_INTENT -> RESTORED   (rollback)
 * </pre>
 */
enum OperationStep {

    /**
     * Nothing of this operation has been touched.
     */
    PENDING,

    /**
     * The original may be moving or copying to the backup.
     */
    BACKUP_INTENT,

    /**
     * The original, if any, is safely in the backup.
     */
    BACKED_UP,

    /**
     * The new content may be moving into the installation.
     */
    INSTALL_INTENT,

    /**
     * The new content is installed.
     */
    APPLIED,

    /**
     * The original may be moving back into the installation.
     */
    RESTORE_INTENT,

    /**
     * The installation holds the original state of this item again.
     */
    RESTORED
}
