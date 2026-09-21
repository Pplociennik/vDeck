package com.github.pplociennik.vdeck.feature.authentication.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies annotation-based validation and secret redaction of {@link InitialPasswordChangeRequest}.
 */
class InitialPasswordChangeRequestTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private InitialPasswordChangeRequest underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an isolated Bean Validation validator and a valid request before each test.
     */
    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        underTest = new InitialPasswordChangeRequest( "Replacement-1", "Replacement-1" );
    }

    // =================================================================================================
    // AFTER METHODS
    // =================================================================================================

    /**
     * Releases resources owned by the Bean Validation provider.
     */
    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a complete request satisfying the declared lengths has no validation violations.
     */
    @Test
    void shouldHaveNoViolations_whenAllFieldsAreValid() {
        // GIVEN
        // the valid request created in setUp

        // WHEN
        var violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that omitted or empty new passwords are rejected by declarative validation.
     *
     * @param newPassword
     *         missing or empty value being validated
     */
    @ParameterizedTest( name = "[{index}] newPassword={0}" )
    @NullAndEmptySource
    void shouldHaveViolation_whenNewPasswordIsMissing( String newPassword ) {
        // GIVEN
        underTest = new InitialPasswordChangeRequest( newPassword, newPassword );

        // WHEN
        var violations = validator.validate( underTest );

        // THEN
        assertThat( violations )
                .extracting( violation -> violation.getPropertyPath().toString() )
                .contains( "newPassword" );
    }

    /**
     * Verifies that blank and shorter-than-required replacement passwords are rejected.
     *
     * @param newPassword
     *         invalid replacement value being validated
     */
    @ParameterizedTest( name = "[{index}] newPassword=<{0}>" )
    @ValueSource( strings = { "   ", "short" } )
    void shouldHaveViolation_whenNewPasswordIsBlankOrTooShort( String newPassword ) {
        // GIVEN
        underTest = new InitialPasswordChangeRequest( newPassword, newPassword );

        // WHEN
        var violations = validator.validate( underTest );

        // THEN
        assertThat( violations )
                .extracting( violation -> violation.getPropertyPath().toString() )
                .contains( "newPassword" );
    }

    /**
     * Verifies that the DTO never exposes either of its secret values through the record's textual
     * representation.
     */
    @Test
    void shouldRedactEveryPassword_whenConvertedToString() {
        // GIVEN
        String newPassword = underTest.newPassword();
        String confirmation = underTest.confirmation();

        // WHEN
        String result = underTest.toString();

        // THEN
        assertThat( result )
                .doesNotContain( newPassword )
                .doesNotContain( confirmation )
                .contains( "<redacted>" );
    }
}