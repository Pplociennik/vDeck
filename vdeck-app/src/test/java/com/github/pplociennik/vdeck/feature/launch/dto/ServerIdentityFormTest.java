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
 * Verifies annotation-based validation and the conversion from the stored profile of
 * {@link ServerIdentityForm}.
 */
class ServerIdentityFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private ServerIdentityForm underTest;

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
        underTest = form( "My server", 2456, "" );
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
        Set< ConstraintViolation< ServerIdentityForm > > violations = validator.validate( underTest );

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
        underTest = form( "My server", port, "" );

        // WHEN
        Set< ConstraintViolation< ServerIdentityForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "port" );
    }

    /**
     * Verifies that a server name which is blank, too long or would break the quoting of the command line
     * is rejected.
     *
     * @param serverName
     *         server name breaking one of the rules
     */
    @ParameterizedTest( name = "[{index}] serverName=\"{0}\"" )
    @ValueSource( strings = { "", "   ", "My \"server\"",
            "12345678901234567890123456789012345678901234567890123456789012345" } )
    void shouldReportServerNameViolation_whenNameBreaksRules( String serverName ) {
        // GIVEN
        underTest = form( serverName, 2456, "" );

        // WHEN
        Set< ConstraintViolation< ServerIdentityForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsOnly( "serverName" );
    }

    /**
     * Verifies that an instance identifier outside the allowed characters or length is rejected.
     *
     * @param instanceId
     *         identifier breaking the allowed format
     */
    @ParameterizedTest( name = "[{index}] instanceId=\"{0}\"" )
    @ValueSource( strings = { "a b", "a.b", "123456789012345678901234567890123" } )
    void shouldReportInstanceIdViolation_whenIdentifierBreaksFormat( String instanceId ) {
        // GIVEN
        underTest = form( "My server", 2456, instanceId );

        // WHEN
        Set< ConstraintViolation< ServerIdentityForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "instanceId" );
    }

    /**
     * Verifies that a form created from a stored profile shows every identity value of the profile.
     */
    @Test
    void shouldFillIdentityFields_whenCreatedFromProfile() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Dedicated", true, false, true, "a1",
                "worlds", 900, 3, 3600, 7200, WorldPreset.HARD, Map.of(), Set.of() );

        // WHEN
        underTest = ServerIdentityForm.from( profile );

        // THEN
        assertThat( underTest ).isEqualTo( new ServerIdentityForm( "My server", 2457, false, true, "a1" ) );
    }

    /**
     * Verifies that the form holds no secret, so the copy shown again after a rejection is the form itself.
     */
    @Test
    void shouldReturnItself_whenCopiedWithoutSecrets() {
        // GIVEN
        // the valid form created in setUp

        // WHEN
        LaunchProfileForm result = underTest.withoutSecrets();

        // THEN
        assertThat( result ).isSameAs( underTest );
    }

    /**
     * Verifies that a form cannot be created from a missing profile.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile at all

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerIdentityForm.from( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "profile must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static ServerIdentityForm form( String serverName, int port, String instanceId ) {
        return new ServerIdentityForm( serverName, port, true, false, instanceId );
    }
}
