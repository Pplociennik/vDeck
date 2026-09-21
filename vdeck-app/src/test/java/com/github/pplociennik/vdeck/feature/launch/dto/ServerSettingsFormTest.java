package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies annotation-based validation, the conversion from the stored profile and the secret redaction
 * of {@link ServerSettingsForm}.
 */
class ServerSettingsFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private ServerSettingsForm underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an isolated Bean Validation validator and a valid form before each test.
     */
    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        underTest = form( 2456, "Dedicated", "Secret1" );
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
     * Verifies that a complete form within every declared range has no violations.
     */
    @Test
    void shouldHaveNoViolations_whenAllFieldsAreValid() {
        // GIVEN
        // the valid form created in setUp

        // WHEN
        Set< ConstraintViolation< ServerSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that an empty password is valid, because it means that the stored password is kept.
     */
    @Test
    void shouldHaveNoViolations_whenPasswordIsEmpty() {
        // GIVEN
        underTest = form( 2456, "Dedicated", "" );

        // WHEN
        Set< ConstraintViolation< ServerSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that ports outside the range usable by the server, which also occupies the next port,
     * are rejected.
     *
     * @param port
     *         port outside 1024-65534
     */
    @ParameterizedTest( name = "[{index}] port={0}" )
    @ValueSource( ints = { 0, 1023, 65535 } )
    void shouldReportPortViolation_whenPortIsOutOfRange( int port ) {
        // GIVEN
        underTest = form( port, "Dedicated", "" );

        // WHEN
        Set< ConstraintViolation< ServerSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "port" );
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
        underTest = form( 2456, "Dedicated", password );

        // WHEN
        Set< ConstraintViolation< ServerSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "password" );
    }

    /**
     * Verifies that a world name which could not be a plain file name is rejected.
     *
     * @param worldName
     *         world name breaking the allowed format
     */
    @ParameterizedTest( name = "[{index}] worldName=\"{0}\"" )
    @ValueSource( strings = { "", " Dedicated", "Dedicated ", "my.world", "../world", "world/sub" } )
    void shouldReportWorldNameViolation_whenNameIsNotPlain( String worldName ) {
        // GIVEN
        underTest = form( 2456, worldName, "" );

        // WHEN
        Set< ConstraintViolation< ServerSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .contains( "worldName" );
    }

    /**
     * Verifies that a form created from a stored profile shows every server value and never carries a
     * password.
     */
    @Test
    void shouldFillFieldsWithoutPassword_whenCreatedFromProfile() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Dedicated", true, false, true, "a1",
                "worlds", 900, 3, 3600, 7200, WorldPreset.HARD, Map.of(), Set.of() );

        // WHEN
        underTest = ServerSettingsForm.from( profile );

        // THEN
        assertThat( underTest ).isEqualTo( new ServerSettingsForm( "My server", 2457, "Dedicated", null, false,
                false, true, "a1", "worlds", 900, 3, 3600, 7200 ) );
    }

    /**
     * Verifies that the copy shown again after a rejection has the password removed and keeps
     * everything else.
     */
    @Test
    void shouldDropOnlyPassword_whenCopiedWithoutPassword() {
        // GIVEN
        // the valid form with a password created in setUp

        // WHEN
        ServerSettingsForm result = underTest.withoutPassword();

        // THEN
        assertThat( result.password() ).isNull();
        assertThat( result ).usingRecursiveComparison().ignoringFields( "password" ).isEqualTo( underTest );
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
        assertThat( result ).doesNotContain( "Secret1" ).contains( "password=<redacted>" );
    }

    /**
     * Verifies that a form cannot be created from a missing profile.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile at all

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerSettingsForm.from( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "profile must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static ServerSettingsForm form( int port, String worldName, String password ) {
        return new ServerSettingsForm( "My server", port, worldName, password, false, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200 );
    }
}
