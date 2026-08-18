package com.github.pplociennik.vdeck.feature.authentication.domain;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import static java.util.Objects.requireNonNull;

/**
 * Persistent credentials and login-security state of one vDeck panel user.
 * Passwords are held only as adaptive one-way hashes and are never exposed through {@code toString()}.
 */
@Entity
@Table( name = "panel_users" )
public class PanelUser {

    @Id
    @Column( nullable = false, updatable = false, length = 64 )
    private String username;

    @Column( name = "password_hash", nullable = false, length = 255 )
    private String passwordHash;

    @Enumerated( EnumType.STRING )
    @Column( nullable = false, length = 32 )
    private PanelRole role;

    @Column( name = "password_change_required", nullable = false )
    private boolean passwordChangeRequired;

    /**
     * Creates an empty instance for JPA.
     */
    protected PanelUser() {
    }

    /**
     * Creates a panel user with validated credentials and role.
     *
     * @param username
     *         unique login name
     * @param passwordHash
     *         adaptive one-way password hash, including its encoder identifier
     * @param role
     *         authorization role assigned to the user
     * @param passwordChangeRequired
     *         whether the user must replace the current password before using the panel
     */
    public PanelUser( @Nonnull String username,
                      @Nonnull String passwordHash,
                      @Nonnull PanelRole role,
                      boolean passwordChangeRequired ) {
        this.username = requireNonBlank( username, "username" );
        this.passwordHash = requireNonBlank( passwordHash, "passwordHash" );
        this.role = requireNonNull( role, "role must not be null" );
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Returns the unique login name.
     *
     * @return unique login name
     */
    public @Nonnull String getUsername() {
        return username;
    }

    /**
     * Returns the stored adaptive password hash.
     *
     * @return encoded password, never the plaintext password
     */
    public @Nonnull String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Returns the authorization role assigned to the user.
     *
     * @return assigned panel role
     */
    public @Nonnull PanelRole getRole() {
        return role;
    }

    /**
     * Reports whether the user must replace the current password.
     *
     * @return {@code true} when access must remain limited to changing the password
     */
    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /**
     * Replaces the stored password hash and removes the first-login restriction.
     *
     * @param newPasswordHash
     *         adaptive one-way hash of the replacement password
     */
    public void changePassword( @Nonnull String newPasswordHash ) {
        passwordHash = requireNonBlank( newPasswordHash, "newPasswordHash" );
        passwordChangeRequired = false;
    }

    private static @Nonnull String requireNonBlank( @Nonnull String value, @Nonnull String name ) {
        requireNonNull( value, name + " must not be null" );
        if ( value.isBlank() ) {
            throw new IllegalArgumentException( name + " must not be blank" );
        }
        return value;
    }
}
