package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies annotation-based validation and the conversion from the stored profile of
 * {@link ServerBackupsForm}.
 */
class ServerBackupsFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private ServerBackupsForm underTest;

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
        underTest = new ServerBackupsForm( 4, 7200, 43200 );
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
     * Verifies that values on the edges of every declared range have no violations.
     *
     * @param backupCount
     *         number of backups
     * @param backupShort
     *         interval before the first backup, in seconds
     * @param backupLong
     *         interval between the subsequent backups, in seconds
     */
    @ParameterizedTest( name = "[{index}] count={0}, short={1}, long={2}" )
    @CsvSource( { "1, 60, 60", "100, 604800, 2592000" } )
    void shouldHaveNoViolations_whenValuesAreOnRangeEdges( int backupCount, int backupShort, int backupLong ) {
        // GIVEN
        underTest = new ServerBackupsForm( backupCount, backupShort, backupLong );

        // WHEN
        Set< ConstraintViolation< ServerBackupsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that a value just outside its range is reported for that field only.
     *
     * @param backupCount
     *         number of backups
     * @param backupShort
     *         interval before the first backup, in seconds
     * @param backupLong
     *         interval between the subsequent backups, in seconds
     * @param field
     *         field expected to be reported
     */
    @ParameterizedTest( name = "[{index}] count={0}, short={1}, long={2} -> {3}" )
    @CsvSource( { "0, 7200, 43200, backupCount", "101, 7200, 43200, backupCount",
            "4, 59, 43200, backupShort", "4, 604801, 43200, backupShort",
            "4, 7200, 59, backupLong", "4, 7200, 2592001, backupLong" } )
    void shouldReportViolation_whenValueIsOutOfRange( int backupCount, int backupShort, int backupLong,
                                                      String field ) {
        // GIVEN
        underTest = new ServerBackupsForm( backupCount, backupShort, backupLong );

        // WHEN
        Set< ConstraintViolation< ServerBackupsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( field );
    }

    /**
     * Verifies that a missing value is reported, because every backup setting is required.
     */
    @Test
    void shouldReportViolation_whenValueIsMissing() {
        // GIVEN
        underTest = new ServerBackupsForm( null, 7200, 43200 );

        // WHEN
        Set< ConstraintViolation< ServerBackupsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "backupCount" );
    }

    /**
     * Verifies that a form created from a stored profile shows its backup values.
     */
    @Test
    void shouldFillBackupFields_whenCreatedFromProfile() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Dedicated", true, false, true, "",
                "worlds", 900, 3, 3600, 7200, null, Map.of(), Set.of() );

        // WHEN
        underTest = ServerBackupsForm.from( profile );

        // THEN
        assertThat( underTest ).isEqualTo( new ServerBackupsForm( 3, 3600, 7200 ) );
    }

    /**
     * Verifies that a form cannot be created from a missing profile.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile at all

        // WHEN
        Throwable thrown = catchThrowable( () -> ServerBackupsForm.from( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "profile must not be null" );
    }
}
