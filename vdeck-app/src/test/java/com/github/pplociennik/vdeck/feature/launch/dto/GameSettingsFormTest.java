package com.github.pplociennik.vdeck.feature.launch.dto;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies annotation-based validation, the conversion from the stored profile and the accessors of
 * {@link GameSettingsForm}.
 */
class GameSettingsFormTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ValidatorFactory validatorFactory;

    private Validator validator;

    private GameSettingsForm underTest;

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
        underTest = new GameSettingsForm( "", "veryhard", "", "", "none", "", null, null, null, null );
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
     * Verifies that a form with short option values has no violations.
     */
    @Test
    void shouldHaveNoViolations_whenAllFieldsAreValid() {
        // GIVEN
        // the valid form created in setUp

        // WHEN
        Set< ConstraintViolation< GameSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).isEmpty();
    }

    /**
     * Verifies that an option value longer than any known option is rejected.
     */
    @Test
    void shouldReportPresetViolation_whenPresetIsTooLong() {
        // GIVEN
        underTest = new GameSettingsForm( "a".repeat( 17 ), "", "", "", "", "", null, null, null, null );

        // WHEN
        Set< ConstraintViolation< GameSettingsForm > > violations = validator.validate( underTest );

        // THEN
        assertThat( violations ).extracting( violation -> violation.getPropertyPath().toString() )
                .containsExactly( "preset" );
    }

    /**
     * Verifies that a form created from a stored profile shows the preset, the modifiers and the keys
     * in their submitted form.
     */
    @Test
    void shouldFillFields_whenCreatedFromProfile() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Dedicated", true, false, true, "a1",
                "worlds", 900, 3, 3600, 7200, WorldPreset.HARD, Map.of( WorldModifier.RAIDS, "none" ),
                Set.of( WorldKey.NO_MAP ) );

        // WHEN
        underTest = GameSettingsForm.from( profile );

        // THEN
        assertThat( underTest.preset() ).isEqualTo( "hard" );
        assertThat( underTest.modifierValue( WorldModifier.RAIDS ) ).isEqualTo( "none" );
        assertThat( underTest.modifierValue( WorldModifier.COMBAT ) ).isEmpty();
        assertThat( underTest.isKeyEnabled( WorldKey.NO_MAP ) ).isTrue();
        assertThat( underTest.isKeyEnabled( WorldKey.NO_BUILD_COST ) ).isFalse();
    }

    /**
     * Verifies that a form created from a profile without a preset shows an empty preset.
     */
    @Test
    void shouldShowEmptyPreset_whenProfileHasNoPreset() {
        // GIVEN
        LaunchProfile profile = new LaunchProfile( "My server", 2457, "Dedicated", false, false, false, "",
                "worlds", 900, 3, 3600, 7200, null, Map.of(), Set.of() );

        // WHEN
        underTest = GameSettingsForm.from( profile );

        // THEN
        assertThat( underTest.preset() ).isEmpty();
    }

    /**
     * Verifies that an unchecked checkbox, which the browser does not submit, reads as disabled.
     */
    @Test
    void shouldTreatMissingCheckboxAsDisabled_whenKeyIsRead() {
        // GIVEN
        // the valid form created in setUp, with every key checkbox left unsubmitted

        // WHEN
        boolean result = underTest.isKeyEnabled( WorldKey.PASSIVE_MOBS );

        // THEN
        assertThat( result ).isFalse();
    }

    /**
     * Verifies that a preset value naming no preset reads as no preset.
     */
    @Test
    void shouldReturnNoPreset_whenPresetValueIsUnknown() {
        // GIVEN
        underTest = new GameSettingsForm( "legendary", "", "", "", "", "", null, null, null, null );

        // WHEN
        WorldPreset result = underTest.presetValue();

        // THEN
        assertThat( result ).isNull();
    }

    /**
     * Verifies that reading a modifier requires naming one.
     */
    @Test
    void shouldThrowException_whenModifierIsNull() {
        // GIVEN
        // no modifier at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.modifierValue( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "modifier must not be null" );
    }

    /**
     * Verifies that a form cannot be created from a missing profile.
     */
    @Test
    void shouldThrowException_whenProfileIsNull() {
        // GIVEN
        // no profile at all

        // WHEN
        Throwable thrown = catchThrowable( () -> GameSettingsForm.from( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "profile must not be null" );
    }
}
