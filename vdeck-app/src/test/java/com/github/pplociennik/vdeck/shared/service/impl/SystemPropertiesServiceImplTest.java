package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link SystemPropertiesServiceImpl} loads, repairs and atomically persists one external
 * properties file.
 *
 * <p>A test-local property set is used instead of {@link VdeckProperty} so that the generic contract
 * of the service is verified independently of the settings vDeck currently happens to declare.
 */
class SystemPropertiesServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private Path settingsFile;

    private SystemPropertiesServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service bootstrapping an isolated settings file with default values.
     */
    @BeforeEach
    void setUp() {
        settingsFile = directory.resolve( "test.properties" );
        underTest = createUnderTest();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a missing settings file is created with every managed key set to its default.
     */
    @Test
    void shouldCreateFileWithDefaultValues_whenFileDoesNotExist() throws IOException {
        // GIVEN
        // the service created in setUp against a directory holding no settings file

        // WHEN
        Properties storedProperties = readSettingsFile();

        // THEN
        assertThat( settingsFile ).exists();
        assertThat( storedProperties ).containsOnly(
                Map.entry( TestProperty.FIRST.getName(), "first-default" ),
                Map.entry( TestProperty.SECOND.getName(), "second-default" ),
                Map.entry( TestProperty.OPTIONAL.getName(), "" ) );
    }

    /**
     * Verifies that the missing parent directories of a new settings file are created together with it,
     * which is how the configuration directory of a fresh installation comes into existence.
     */
    @Test
    void shouldCreateParentDirectories_whenTheyDoNotExist() {
        // GIVEN
        settingsFile = directory.resolve( ".vDeck" ).resolve( "config" ).resolve( "test.properties" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( settingsFile ).isRegularFile();
    }

    /**
     * Verifies that an empty value of an optional setting is a legitimate value and survives a reload
     * instead of being replaced by the default.
     */
    @Test
    void shouldKeepEmptyValue_whenOptionalPropertyIsEmptyInFile() throws IOException {
        // GIVEN
        writeSettingsFile( "optional.key=\nfirst.key=configured\nsecond.key=configured" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( underTest.getValue( TestProperty.OPTIONAL ) ).isEmpty();
    }

    /**
     * Verifies that an optional setting whose key is missing from the file is restored with its default.
     */
    @Test
    void shouldRestoreOptionalKey_whenItIsMissingInFile() throws IOException {
        // GIVEN
        writeSettingsFile( "first.key=configured\nsecond.key=configured" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( readSettingsFile() ).containsEntry( TestProperty.OPTIONAL.getName(), "" );
    }

    /**
     * Verifies that an optional setting accepts a blank value, stored as empty.
     */
    @Test
    void shouldPersistEmptyValue_whenOptionalPropertyIsCleared() throws IOException {
        // GIVEN
        underTest.setValue( TestProperty.OPTIONAL, "configured" );

        // WHEN
        underTest.setValue( TestProperty.OPTIONAL, "   " );

        // THEN
        assertThat( underTest.getValue( TestProperty.OPTIONAL ) ).isEmpty();
        assertThat( readSettingsFile() ).containsEntry( TestProperty.OPTIONAL.getName(), "" );
    }

    /**
     * Verifies that several values written together are all visible in memory and persisted.
     */
    @Test
    void shouldPersistEveryValue_whenValuesAreWrittenTogether() throws IOException {
        // GIVEN
        Map< SystemPropertyIf, String > values = new LinkedHashMap<>();
        values.put( TestProperty.FIRST, " first-changed " );
        values.put( TestProperty.OPTIONAL, "optional-changed" );

        // WHEN
        underTest.setValues( values );

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "first-changed" );
        assertThat( readSettingsFile() )
                .containsEntry( TestProperty.FIRST.getName(), "first-changed" )
                .containsEntry( TestProperty.OPTIONAL.getName(), "optional-changed" )
                .containsEntry( TestProperty.SECOND.getName(), "second-default" );
    }

    /**
     * Verifies that a blank value of a mandatory setting rejects the whole batch before anything
     * changes, including the valid entries of the same batch.
     */
    @Test
    void shouldChangeNothing_whenBatchContainsBlankMandatoryValue() throws IOException {
        // GIVEN
        Map< SystemPropertyIf, String > values = new LinkedHashMap<>();
        values.put( TestProperty.OPTIONAL, "optional-changed" );
        values.put( TestProperty.FIRST, " " );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( values ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "value must not be blank" );
        assertThat( underTest.getValue( TestProperty.OPTIONAL ) ).isEmpty();
        assertThat( readSettingsFile() ).containsEntry( TestProperty.OPTIONAL.getName(), "" );
    }

    /**
     * Verifies that a batch containing a setting of another file is rejected as a programming error.
     */
    @Test
    void shouldThrowException_whenBatchContainsUnmanagedProperty() {
        // GIVEN
        Map< SystemPropertyIf, String > values = Map.of( UnmanagedProperty.UNMANAGED, "value" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( values ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "is not managed by file" );
    }

    /**
     * Verifies that a failed batch write restores every previous value in memory, so memory and disk
     * cannot diverge.
     */
    @Test
    void shouldKeepEveryPreviousValue_whenBatchWriteFails() throws IOException {
        // GIVEN
        Files.delete( settingsFile );
        Files.delete( directory );
        Map< SystemPropertyIf, String > values = new LinkedHashMap<>();
        values.put( TestProperty.FIRST, "first-changed" );
        values.put( TestProperty.OPTIONAL, "optional-changed" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( values ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to save vDeck settings" );
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "first-default" );
        assertThat( underTest.getValue( TestProperty.OPTIONAL ) ).isEmpty();
    }

    /**
     * Verifies that a null batch is rejected by the method contract.
     */
    @Test
    void shouldThrowException_whenWrittenValuesAreNull() {
        // GIVEN
        // no values at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "values must not be null" );
    }

    /**
     * Verifies that a value present in the file is returned instead of the default.
     */
    @Test
    void shouldReturnStoredValue_whenPropertyExistsInFile() throws IOException {
        // GIVEN
        writeSettingsFile( "first.key=configured" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "configured" );
    }

    /**
     * Verifies that the default is used when the file does not declare a managed key.
     */
    @Test
    void shouldReturnDefaultValue_whenPropertyIsMissingInFile() throws IOException {
        // GIVEN
        writeSettingsFile( "second.key=configured" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "first-default" );
    }

    /**
     * Verifies that a blank value in the file is treated as missing and replaced by the default.
     */
    @ParameterizedTest( name = "[{index}] stored value=\"{0}\"" )
    @ValueSource( strings = { "", " ", "\t" } )
    void shouldReturnDefaultValue_whenStoredValueIsBlank( String storedValue ) throws IOException {
        // GIVEN
        writeSettingsFile( "first.key=" + storedValue );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "first-default" );
    }

    /**
     * Verifies that whitespace around a stored value is removed when the value is read.
     */
    @Test
    void shouldStripSurroundingWhitespace_whenValueIsRead() throws IOException {
        // GIVEN
        writeSettingsFile( "first.key=   spaced   " );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "spaced" );
    }

    /**
     * Verifies that a managed key missing from the file is written back, which is the migration path
     * for settings introduced by a new release.
     */
    @Test
    void shouldRepairFileOnDisk_whenManagedKeyIsMissing() throws IOException {
        // GIVEN
        writeSettingsFile( "second.key=configured" );

        // WHEN
        underTest = createUnderTest();

        // THEN
        assertThat( readSettingsFile() ).containsEntry( TestProperty.FIRST.getName(), "first-default" );
    }

    /**
     * Verifies that entries not owned by the service survive a rewrite of the file.
     */
    @Test
    void shouldPreserveForeignEntries_whenFileIsRewritten() throws IOException {
        // GIVEN
        writeSettingsFile( "first.key=configured\nsecond.key=configured\nforeign.key=kept" );
        underTest = createUnderTest();

        // WHEN
        underTest.setValue( TestProperty.FIRST, "changed" );

        // THEN
        assertThat( readSettingsFile() ).containsEntry( "foreign.key", "kept" );
    }

    /**
     * Verifies that a written value is visible in memory and persisted to the file.
     */
    @Test
    void shouldPersistValue_whenValueIsWritten() throws IOException {
        // GIVEN
        String newValue = "changed";

        // WHEN
        underTest.setValue( TestProperty.FIRST, newValue );

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( newValue );
        assertThat( readSettingsFile() ).containsEntry( TestProperty.FIRST.getName(), newValue );
    }

    /**
     * Verifies that whitespace around a written value is removed before it is persisted.
     */
    @Test
    void shouldStripSurroundingWhitespace_whenValueIsWritten() throws IOException {
        // GIVEN
        String newValue = "   changed   ";

        // WHEN
        underTest.setValue( TestProperty.FIRST, newValue );

        // THEN
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "changed" );
    }

    /**
     * Verifies that the temporary file used for the atomic replace is not left behind.
     */
    @Test
    void shouldLeaveNoTemporaryFile_whenValueIsWritten() throws IOException {
        // GIVEN
        // a freshly bootstrapped settings file

        // WHEN
        underTest.setValue( TestProperty.FIRST, "changed" );

        // THEN
        try ( var entries = Files.list( directory ) ) {
            assertThat( entries ).containsExactly( settingsFile );
        }
    }

    /**
     * Verifies that a failed write leaves the in-memory value unchanged, so memory and disk cannot
     * diverge.
     */
    @Test
    void shouldKeepPreviousValue_whenWriteFails() throws IOException {
        // GIVEN
        Files.delete( settingsFile );
        Files.delete( directory );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( TestProperty.FIRST, "changed" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to save vDeck settings" );
        assertThat( underTest.getValue( TestProperty.FIRST ) ).isEqualTo( "first-default" );
    }

    /**
     * Verifies that a blank value is rejected instead of being persisted.
     */
    @ParameterizedTest( name = "[{index}] value=\"{0}\"" )
    @ValueSource( strings = { "", "   ", "\t" } )
    void shouldThrowException_whenWrittenValueIsBlank( String value ) {
        // GIVEN
        // a freshly bootstrapped settings file

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( TestProperty.FIRST, value ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "value must not be blank" );
    }

    /**
     * Verifies that reading a setting owned by another file is reported as a programming error.
     */
    @Test
    void shouldThrowException_whenReadPropertyIsNotManaged() {
        // GIVEN
        SystemPropertyIf unmanagedProperty = UnmanagedProperty.UNMANAGED;

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( unmanagedProperty ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "is not managed by file" );
    }

    /**
     * Verifies that writing a setting owned by another file is reported as a programming error.
     */
    @Test
    void shouldThrowException_whenWrittenPropertyIsNotManaged() {
        // GIVEN
        SystemPropertyIf unmanagedProperty = UnmanagedProperty.UNMANAGED;

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( unmanagedProperty, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "is not managed by file" );
    }

    /**
     * Verifies that an unreadable settings file aborts construction instead of silently using defaults.
     */
    @Test
    void shouldThrowException_whenFileCannotBeRead() throws IOException {
        // GIVEN
        Files.delete( settingsFile );
        Files.createDirectory( settingsFile );

        // WHEN
        Throwable thrown = catchThrowable( this::createUnderTest );

        // THEN
        assertThat( thrown )
                .isInstanceOf( UncheckedIOException.class )
                .hasMessageContaining( "Failed to read vDeck settings" );
    }

    /**
     * Verifies that a relative settings file path is rejected, because it would depend on the process
     * working directory.
     */
    @Test
    void shouldThrowException_whenPropertiesFileIsNotAbsolute() {
        // GIVEN
        Path relativeFile = Path.of( "test.properties" );

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesServiceImpl( relativeFile, defaults() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "propertiesFile must be absolute" );
    }

    /**
     * Verifies that a blank default value of a mandatory setting is rejected, because it would break the
     * guarantee that a mandatory setting is never blank.
     */
    @Test
    void shouldThrowException_whenDefaultValueIsBlank() {
        // GIVEN
        Map< SystemPropertyIf, String > blankDefaults = Map.of( TestProperty.FIRST, "  " );

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesServiceImpl( settingsFile, blankDefaults ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "must not be blank" );
    }

    /**
     * Verifies that a null settings file is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenPropertiesFileIsNull() {
        // GIVEN
        // no settings file at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesServiceImpl( null, defaults() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "propertiesFile must not be null" );
    }

    /**
     * Verifies that a null default value set is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenDefaultValuesAreNull() {
        // GIVEN
        // no default values at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesServiceImpl( settingsFile, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "defaultValues must not be null" );
    }

    /**
     * Verifies that reading a null setting is rejected before the lookup happens.
     */
    @Test
    void shouldThrowException_whenReadPropertyIsNull() {
        // GIVEN
        // no setting at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "property must not be null" );
    }

    /**
     * Verifies that writing a null setting is rejected before the file is touched.
     */
    @Test
    void shouldThrowException_whenWrittenPropertyIsNull() {
        // GIVEN
        // no setting at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( null, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "property must not be null" );
    }

    /**
     * Verifies that writing a null value is rejected before the file is touched.
     */
    @Test
    void shouldThrowException_whenWrittenValueIsNull() {
        // GIVEN
        // no value at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( TestProperty.FIRST, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "value must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private SystemPropertiesServiceImpl createUnderTest() {
        return new SystemPropertiesServiceImpl( settingsFile, defaults() );
    }

    private static Map< SystemPropertyIf, String > defaults() {
        Map< SystemPropertyIf, String > defaults = new LinkedHashMap<>();
        defaults.put( TestProperty.FIRST, "first-default" );
        defaults.put( TestProperty.SECOND, "second-default" );
        defaults.put( TestProperty.OPTIONAL, "" );
        return defaults;
    }

    private void writeSettingsFile( String content ) throws IOException {
        Files.writeString( settingsFile, content, StandardCharsets.UTF_8 );
    }

    private Properties readSettingsFile() throws IOException {
        Properties properties = new Properties();
        try ( BufferedReader reader = Files.newBufferedReader( settingsFile, StandardCharsets.UTF_8 ) ) {
            properties.load( reader );
        }
        return properties;
    }

    /**
     * Test-local property set keeping the verified contract independent of {@link VdeckProperty}.
     */
    private enum TestProperty implements SystemPropertyIf {

        FIRST( "first.key", true ),
        SECOND( "second.key", true ),
        OPTIONAL( "optional.key", false );

        private final String name;

        private final boolean mandatory;

        TestProperty( String name, boolean mandatory ) {
            this.name = name;
            this.mandatory = mandatory;
        }

        @Override
        public @Nonnull String getName() {
            return name;
        }

        @Override
        public boolean isMandatory() {
            return mandatory;
        }
    }

    /**
     * Setting owned by another file, used to verify that the service rejects it.
     */
    private enum UnmanagedProperty implements SystemPropertyIf {

        UNMANAGED;

        @Override
        public @Nonnull String getName() {
            return "unmanaged.key";
        }

        @Override
        public boolean isMandatory() {
            return true;
        }
    }
}
