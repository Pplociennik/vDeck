package com.github.pplociennik.vdeck.feature.releasenotes.persistence;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence operations for acknowledged change lists, keyed by version text.
 */
public interface ReleaseNotesAcknowledgementRepository extends JpaRepository< ReleaseNotesAcknowledgement, String > {
}
