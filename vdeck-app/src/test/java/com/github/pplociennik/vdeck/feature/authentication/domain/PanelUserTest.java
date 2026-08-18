package com.github.pplociennik.vdeck.feature.authentication.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies credential invariants and password replacement behavior of {@link PanelUser}.
 */
class PanelUserTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private PanelUser underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh administrator still restricted by its initial password.
     */
    @BeforeEach
    void setUp() {
        underTest = new PanelUser( "admin", "{bcrypt}initial", PanelRole.ADMIN, true );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that construction preserves the supplied identity, encoded password, role and
     * first-login restriction.
     */
    @Test
    void shouldExposeCreatedState_whenValuesAreValid() {
        // GIVEN
        // the user created in setUp

        // WHEN
        String username = underTest.getUsername();

        // THEN
        assertThat( username ).isEqualTo( "admin" );
        assertThat( underTest.getPasswordHash() ).isEqualTo( "{bcrypt}initial" );
        assertThat( underTest.getRole() ).isEqualTo( PanelRole.ADMIN );
        assertThat( underTest.isPasswordChangeRequired() ).isTrue();
    }

    /**
     * Verifies that replacing the password stores the new hash and removes the first-login restriction.
     */
    @Test
    void shouldRemoveFirstLoginRestriction_whenPasswordChanges() {
        // GIVEN
        String replacementHash = "{bcrypt}replacement";

        // WHEN
        underTest.changePassword( replacementHash );

        // THEN
        assertThat( underTest.getPasswordHash() ).isEqualTo( replacementHash );
        assertThat( underTest.isPasswordChangeRequired() ).isFalse();
    }

    /**
     * Verifies that null constructor values are rejected before an invalid entity can be created.
     */
    @Test
    void shouldThrowException_whenRequiredConstructorValueIsNull() {
        // GIVEN
        // no username

        // WHEN
        Throwable thrown = catchThrowable( () -> new PanelUser(
                null, "{bcrypt}hash", PanelRole.ADMIN, true ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "username must not be null" );
    }

    /**
     * Verifies that blank credential values are rejected rather than persisted as usable account data.
     */
    @Test
    void shouldThrowException_whenPasswordHashIsBlank() {
        // GIVEN
        String blankHash = "   ";

        // WHEN
        Throwable thrown = catchThrowable( () -> new PanelUser( "admin", blankHash, PanelRole.ADMIN, true ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "passwordHash must not be blank" );
    }

    /**
     * Verifies that a null role is rejected by the entity constructor contract.
     */
    @Test
    void shouldThrowException_whenRoleIsNull() {
        // GIVEN
        // no role

        // WHEN
        Throwable thrown = catchThrowable( () -> new PanelUser( "admin", "{bcrypt}hash", null, true ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "role must not be null" );
    }

    /**
     * Verifies that a null replacement hash cannot remove the first-login restriction.
     */
    @Test
    void shouldThrowException_whenReplacementHashIsNull() {
        // GIVEN
        // no replacement hash

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "newPasswordHash must not be null" );
        assertThat( underTest.isPasswordChangeRequired() ).isTrue();
    }
}
