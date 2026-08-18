package com.github.pplociennik.vdeck.feature.authentication.domain;

import com.github.pplociennik.vdeck.feature.authentication.PasswordChangeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PasswordChangeResult} pairs every outcome with the message its consumers show,
 * and refuses combinations that would leave a screen without wording or with a meaningless one.
 */
class PasswordChangeResultTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private PasswordChangeResult underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a successful result before each test; tests describing a rejection replace it.
     */
    @BeforeEach
    void setUp() {
        underTest = PasswordChangeResult.success();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a successful change reports success and carries no message to show.
     */
    @Test
    void shouldCarryNoMessageKey_whenChangeSucceeded() {
        // GIVEN
        // the successful result created in setUp

        // WHEN
        boolean result = underTest.isSuccessful();

        // THEN
        assertThat( result ).isTrue();
        assertThat( underTest.status() ).isEqualTo( PasswordChangeStatus.SUCCESS );
        assertThat( underTest.messageKey() ).isNull();
    }

    /**
     * Verifies that every rejection keeps its status together with the message key it was created with.
     *
     * @param status
     *         rejected outcome being described
     */
    @ParameterizedTest( name = "[{index}] status={0}" )
    @EnumSource( value = PasswordChangeStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SUCCESS" )
    void shouldCarryMessageKey_whenChangeWasRejected( PasswordChangeStatus status ) {
        // GIVEN
        PasswordChangeMessage messageKey = PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK;

        // WHEN
        underTest = PasswordChangeResult.rejected( status, messageKey );

        // THEN
        assertThat( underTest.status() ).isEqualTo( status );
        assertThat( underTest.messageKey() ).isEqualTo( messageKey );
        assertThat( underTest.isSuccessful() ).isFalse();
    }

    /**
     * Verifies that a rejection cannot be created without the message explaining it to the user.
     */
    @Test
    void shouldThrowException_whenRejectionHasNoMessageKey() {
        // GIVEN
        // a rejected status without any message

        // WHEN
        Throwable thrown = catchThrowable(
                () -> new PasswordChangeResult( PasswordChangeStatus.NEW_PASSWORD_MISMATCH, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "rejected password change requires a message key" );
    }

    /**
     * Verifies that a success cannot smuggle in a failure message, which a screen would then display
     * next to a completed operation.
     */
    @Test
    void shouldThrowException_whenSuccessCarriesMessageKey() {
        // GIVEN
        // a successful status paired with a rejection message

        // WHEN
        Throwable thrown = catchThrowable( () -> PasswordChangeResult.rejected(
                PasswordChangeStatus.SUCCESS, PasswordChangeMessage.NEW_PASSWORD_MISMATCH ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "successful password change has no message key" );
    }

    /**
     * Verifies that a result without a status is rejected, because no consumer could classify it.
     */
    @Test
    void shouldThrowException_whenStatusIsNull() {
        // GIVEN
        // no status

        // WHEN
        Throwable thrown = catchThrowable(
                () -> PasswordChangeResult.rejected( null, PasswordChangeMessage.NEW_PASSWORD_MISMATCH ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "status must not be null" );
    }

    /**
     * Verifies that a rejection cannot be created with a null message key through the factory contract.
     */
    @Test
    void shouldThrowException_whenMessageKeyIsNull() {
        // GIVEN
        // no message key

        // WHEN
        Throwable thrown = catchThrowable(
                () -> PasswordChangeResult.rejected( PasswordChangeStatus.NEW_PASSWORD_TOO_WEAK, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "messageKey must not be null" );
    }
}