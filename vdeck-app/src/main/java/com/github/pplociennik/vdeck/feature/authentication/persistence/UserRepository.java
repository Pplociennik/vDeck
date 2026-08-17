package com.github.pplociennik.vdeck.feature.authentication.persistence;

import com.github.pplociennik.vdeck.feature.authentication.domain.PanelUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence operations for vDeck panel users.
 */
public interface UserRepository extends JpaRepository< PanelUser, String > {
}
