package com.github.pplociennik.vdeck.feature.launch.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies annotation-based validation and the secret handling of {@link ServerAccessForm}.
 */
class ServerAccessFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private ServerAccessForm underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an isolated Bean Validation validator and a valid form with a password before each test.
     */
    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        underTest = new ServerAccessForm( "Secret1", true );
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
     * Verifies that a password within every rule has no violations.
     */
    @Test
    void shouldHaveNoViolations_whenPasswordIsValid() {
        // GIVEN
        // the valid form created in setUp

        // WHEN
        Set< ConstraintViolation< ServerAccessForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that an empty or missing password is valid, because it means that the stored password is
     * kept.
     */
    @Test
    void shouldHaveNoViolations_whenPasswordIsEmptyOrMissing() {
        // GIVEN
        ServerAccessForm empty = new ServerAccessForm( "", false );
        ServerAccessForm missing = new ServerAccessForm( null, null );

        // WHEN
        Set< ConstraintViolation< ServerAccessForm > > emptyViolations = validator.validate( empty );
        Set< ConstraintViolation< ServerAccessForm > > missingViolations = validator.validate( missing );

        // THEN
        assertThat( emptyViolations ).isEmpty();
        assertThat( missingViolations ).isEmpty();
    }

    /**
     * Verifies that a password which is too short, too long, padded with whitespace or containing a
     * double quote is rejected.
     *
     * @param password
     *         password breaking one of the rules
     */
    @ParameterizedTest( name = "[{index}] password=\"{0}\"" )
    @ValueSource( strings = { "abcd", " abcde", "abcde ", "abc\"de",
            "12345678901234567890123456789012345678901234567890123456789012345" } )
    void shouldReportPasswordViolation_whenPasswordBreaksRules( String password ) {
        // GIVEN
        underTest = new ServerAccessForm( password, false );

        // WHEN
        Set< ConstraintViolation< ServerAccessForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "password" );
    }

    /**
     * Verifies that the form shown for a stored profile has no password and no removal request.
     */
    @Test
    void shouldHaveNoPasswordAndNoRemoval_whenCreatedEmpty() {
        // GIVEN
        // nothing to prepare

        // WHEN
        ServerAccessForm result = ServerAccessForm.empty();

        // THEN
        assertThat( result ).isEqualTo( new ServerAccessForm( null, false ) );
    }

    /**
     * Verifies that the copy shown again after a rejection has the password removed and keeps the
     * removal request.
     */
    @Test
    void shouldDropOnlyPassword_whenCopiedWithoutSecrets() {
        // GIVEN
        // the valid form with a password created in setUp

        // WHEN
        ServerAccessForm result = underTest.withoutSecrets();

        // THEN
        assertThat( result ).isEqualTo( new ServerAccessForm( null, true ) );
    }

    /**
     * Verifies that the textual representation never contains the submitted password.
     */
    @Test
    void shouldRedactPassword_whenConvertedToString() {
        // GIVEN
        // the valid form with a password created in setUp

        // WHEN
        String result = underTest.toString();

        // THEN
        assertThat( result ).doesNotContain( "Secret1" ).contains( "password=<redacted>", "clearPassword=true" );
    }
}
