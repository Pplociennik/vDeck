package com.github.pplociennik.vdeck.feature.releasenotes.service;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.feature.releasenotes.exc.ReleaseNotesException;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * Decides which version change lists the panel shows after an update and records that the administrator
 * closed them.
 *
 * <p>The installed version is the panel's build version without a {@code -SNAPSHOT} suffix. Without
 * build information, or with a version that is no release version, both operations do nothing.
 */
public interface ReleaseNotesService {

    /**
     * Returns the change lists the administrator has not closed yet, newest first.
     *
     * <p>Nothing is returned once the installed version is acknowledged. Otherwise the result holds every
     * change list newer than the newest acknowledged version and not newer than the installed one; when no
     * version was ever acknowledged, only the installed version's list. Change lists without any change
     * are left out, so the result may be empty even before an acknowledgement.
     *
     * <p>Reads the acknowledgement table and, only when something may be shown, the change list files.
     *
     * @return unmodifiable list of change lists to show; empty when there is nothing to show
     */
    @Nonnull
    List< ReleaseNotes > findUnacknowledged();

    /**
     * Records that the administrator closed the change lists of the installed version. Repeating it is
     * harmless; without a known installed version nothing is stored.
     *
     * @throws ReleaseNotesException
     *         if the record cannot be stored
     */
    void acknowledgeInstalled();
}
