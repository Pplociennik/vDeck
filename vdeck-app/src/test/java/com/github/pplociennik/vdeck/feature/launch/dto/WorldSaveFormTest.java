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
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies annotation-based validation and the conversion from the stored profile of {@link WorldSaveForm}.
 */
class WorldSaveFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private WorldSaveForm underTest;

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
        underTest = new WorldSaveForm( "Dedicated", "valheim-data", 1800 );
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
        Set< ConstraintViolation< WorldSaveForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
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
        underTest = new WorldSaveForm( worldName, "valheim-data", 1800 );

        // WHEN
        Set< ConstraintViolation< WorldSaveForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsOnly( "worldName" );
    }

    /**
     * Verifies that save intervals outside one minute to one day are rejected.
     *
     * @param saveInterval
     *         interval outside 60-86400 seconds
     */
    @ParameterizedTest( name = "[{index}] saveInterval={0}" )
    @ValueSource( ints = { 59, 86_401 } )
    void shouldReportSaveIntervalViolation_whenIntervalIsOutOfRange( int saveInterval ) {
        // GIVEN
        underTest = new WorldSaveForm( "Dedicated", "valheim-data", saveInterval );

        // WHEN
        Set< ConstraintViolation< WorldSaveForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "saveInterval" );
    }

    /**
     * Verifies that a blank save directory is rejected; its location is checked by the service.
     */
    @Test
    void shouldReportSaveDirectoryViolation_whenDirectoryIsBlank() {
        // GIVEN
        underTest = new WorldSaveForm( "Dedicated", " ", 1800 );

        // WHEN
        Set< ConstraintViolation< WorldSaveForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "saveDirectory" );
    }

    /**
     * Verifies that a form created from a stored profile shows its world and save values.
     */
    @Test
    void shouldFillWorldFields_whenCreatedFromProfile() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Midgard", true, false, true, "",
                "worlds", 900, 3, 3600, 7200, null, Map.of(), Set.of() );

        // WHEN
        underTest = WorldSaveForm.from( profile );

        // THEN
        assertThat( underTest ).isEqualTo( new WorldSaveForm( "Midgard", "worlds", 900 ) );
    }

    /**
     * Verifies that a form cannot be created from a missing profile.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile at all

        // WHEN
        Throwable thrown = catchThrowable( () -> WorldSaveForm.from( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "profile must not be null" );
    }
}
