package com.github.pplociennik.vdeck.feature.releasenotes.domain;

import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static java.util.Objects.requireNonNull;

/**
 * Record that the administrator closed the change list of an installed version, so the panel does not
 * show it again. The newest recorded version also marks where the next change list starts.
 */
@Entity
@Table( name = "release_notes_acknowledgements" )
@Getter
@NoArgsConstructor( access = AccessLevel.PROTECTED )
public class ReleaseNotesAcknowledgement {

    /**
     * Acknowledged version in its {@code MAJOR.MINOR.PATCH} form, never a {@code -SNAPSHOT} suffix.
     */
    @Id
    @Column( nullable = false, updatable = false, length = 32 )
    private String version;

    /**
     * Creates the record of an acknowledged version.
     *
     * @param version
     *         version whose change list was closed
     * @throws NullPointerException
     *         if {@code version} is {@code null}
     */
    public ReleaseNotesAcknowledgement( @Nonnull ReleaseVersion version ) {
        this.version = requireNonNull( version, "version must not be null" ).toString();
    }
}
