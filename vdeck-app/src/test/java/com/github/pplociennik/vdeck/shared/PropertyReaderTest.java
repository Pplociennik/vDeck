package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link PropertyReader} interprets stored values of settings and falls back to the value
 * given by the caller when a stored value does not match its setting.
 */
@ExtendWith( MockitoExtension.class )
class PropertyReaderTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String FILE_NAME = "test.properties";

    @Mock
    private SystemPropertiesService service;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the stored value is returned unchanged.
     */
    @Test
    void shouldReturnStoredValue_whenRead() {
        // GIVEN
        storedValue( " value " );

        // WHEN
        String result = PropertyReader.read( service, FILE_NAME, TestProperty.SETTING );

        // THEN
        assertThat( result ).isEqualTo( " value " );
    }

    /**
     * Verifies that a stored number is returned as a number.
     */
    @Test
    void shouldReturnStoredNumber_whenValueIsNumber() {
        // GIVEN
        storedValue( "2457" );

        // WHEN
        int result = PropertyReader.readInt( service, FILE_NAME, TestProperty.SETTING, "2456" );

        // THEN
        assertThat( result ).isEqualTo( 2457 );
    }

    /**
     * Verifies that a stored value which is not a number is replaced by the default.
     */
    @Test
    void shouldReturnDefaultNumber_whenValueIsNotNumber() {
        // GIVEN
        storedValue( "many" );

        // WHEN
        int result = PropertyReader.readInt( service, FILE_NAME, TestProperty.SETTING, "2456" );

        // THEN
        assertThat( result ).isEqualTo( 2456 );
    }

    /**
     * Verifies that a default which is not a number is reported, because it is a programming error.
     */
    @Test
    void shouldThrowException_whenDefaultIsNotNumber() {
        // GIVEN
        storedValue( "many" );

        // WHEN
        Throwable thrown = catchThrowable( () -> PropertyReader.readInt( service, FILE_NAME, TestProperty.SETTING, "few" ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NumberFormatException.class );
    }

    /**
     * Verifies that {@code true} and {@code false} are accepted in any letter case.
     *
     * @param storedValue
     *         stored boolean in a mixed letter case
     */
    @ParameterizedTest
    @ValueSource( strings = { "TRUE", "True", "true" } )
    void shouldReturnStoredBoolean_whenValueIsBooleanInAnyCase( String storedValue ) {
        // GIVEN
        storedValue( storedValue );

        // WHEN
        boolean result = PropertyReader.readBoolean( service, FILE_NAME, TestProperty.SETTING, "false" );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that a stored value which is neither {@code true} nor {@code false} is replaced by the
     * default.
     *
     * @param storedValue
     *         stored value that is not a boolean
     */
    @ParameterizedTest
    @ValueSource( strings = { "yes", "1", "" } )
    void shouldReturnDefaultBoolean_whenValueIsNotBoolean( String storedValue ) {
        // GIVEN
        storedValue( storedValue );

        // WHEN
        boolean result = PropertyReader.readBoolean( service, FILE_NAME, TestProperty.SETTING, "true" );

        // THEN
        assertThat( result ).isTrue();
    }

    /**
     * Verifies that an empty optional setting is read as unused without consulting the parser.
     */
    @Test
    void shouldReturnEmpty_whenOptionalValueIsEmpty() {
        // GIVEN
        storedValue( "" );
        Function< String, Optional< Integer > > parser = value -> {
            throw new AssertionError( "the parser must not be called for an empty value" );
        };

        // WHEN
        Optional< Integer > result = PropertyReader.readOptional( service, FILE_NAME, TestProperty.SETTING, parser );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a recognised optional value is returned in its interpreted form.
     */
    @Test
    void shouldReturnParsedValue_whenOptionalValueIsRecognised() {
        // GIVEN
        storedValue( "hard" );

        // WHEN
        Optional< String > result = PropertyReader.readOptional( service, FILE_NAME, TestProperty.SETTING,
                value -> Optional.of( value.toUpperCase() ) );

        // THEN
        assertThat( result ).contains( "HARD" );
    }

    /**
     * Verifies that an optional value the parser does not recognise is ignored as if it were empty.
     */
    @Test
    void shouldReturnEmpty_whenOptionalValueIsNotRecognised() {
        // GIVEN
        storedValue( "legendary" );

        // WHEN
        Optional< String > result = PropertyReader.readOptional( service, FILE_NAME, TestProperty.SETTING,
                value -> Optional.empty() );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that a read without the service holding the file is rejected.
     */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> PropertyReader.read( null, FILE_NAME, TestProperty.SETTING ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "service must not be null" );
    }

    /**
     * Verifies that a typed read without a default is rejected before anything is read.
     */
    @Test
    void shouldThrowException_whenDefaultIsNull() {
        // GIVEN
        // no default at all

        // WHEN
        Throwable thrown = catchThrowable( () -> PropertyReader.readBoolean( service, FILE_NAME, TestProperty.SETTING,
                null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "defaultValue must not be null" );
    }

    /**
     * Verifies that an optional read without a parser is rejected before anything is read.
     */
    @Test
    void shouldThrowException_whenParserIsNull() {
        // GIVEN
        // no parser at all

        // WHEN
        Throwable thrown = catchThrowable( () -> PropertyReader.readOptional( service, FILE_NAME, TestProperty.SETTING,
                null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "parser must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void storedValue( String value ) {
        given( service.getValue( FILE_NAME, TestProperty.SETTING ) ).willReturn( value );
    }

    /**
     * Test-local setting keeping the verified contract independent of the settings vDeck declares.
     */
    private enum TestProperty implements SystemPropertyIf {

        SETTING;

        @Override
        public @Nonnull String getName() {
            return "test.setting";
        }

        @Override
        public boolean isMandatory() {
            return false;
        }
    }
}
