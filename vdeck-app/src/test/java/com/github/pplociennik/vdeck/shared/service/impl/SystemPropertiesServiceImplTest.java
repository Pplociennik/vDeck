package com.github.pplociennik.vdeck.shared.service.impl;

import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link SystemPropertiesServiceImpl} serves several properties files through one
 * instance, addressing each file by its name. The per-file load, repair and save rules are verified by
 * {@code PropertiesFileTest}; this class covers the file registry and its routing.
 */
@ExtendWith( MockitoExtension.class )
class SystemPropertiesServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String FIRST_FILE = "first.properties";

    private static final String SECOND_FILE = "second.properties";

    @TempDir
    private Path directory;

    @Mock
    private PathService pathService;

    private SystemPropertiesServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service with no file loaded.
     */
    @BeforeEach
    void setUp() {
        underTest = new SystemPropertiesServiceImpl( pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that loading a file creates it with its default values at the location resolved by the
     * path service.
     */
    @Test
    void shouldCreateFileAtResolvedLocation_whenFileIsLoaded() throws IOException {
        // GIVEN
        Path firstFile = resolveConfigurationFile( FIRST_FILE );

        // WHEN
        underTest.loadFile( FIRST_FILE, firstDefaults() );

        // THEN
        assertThat( readFile( firstFile ) )
                .containsEntry( FirstProperty.NAME.getName(), "first-default" )
                .containsEntry( FirstProperty.NOTE.getName(), "" );
    }

    /**
     * Verifies that a value is read from the file named by the caller.
     */
    @Test
    void shouldReturnValueOfNamedFile_whenFileIsLoaded() {
        // GIVEN
        loadBothFiles();

        // WHEN
        String result = underTest.getValue( SECOND_FILE, SecondProperty.PORT );

        // THEN
        assertThat( result ).isEqualTo( "2456" );
    }

    /**
     * Verifies that a written value is persisted to the named file only.
     */
    @Test
    void shouldPersistValueToNamedFile_whenValueIsWritten() throws IOException {
        // GIVEN
        Path firstFile = resolveConfigurationFile( FIRST_FILE );
        Path secondFile = resolveConfigurationFile( SECOND_FILE );
        underTest.loadFile( FIRST_FILE, firstDefaults() );
        underTest.loadFile( SECOND_FILE, secondDefaults() );

        // WHEN
        underTest.setValue( FIRST_FILE, FirstProperty.NAME, "changed" );

        // THEN
        assertThat( underTest.getValue( FIRST_FILE, FirstProperty.NAME ) ).isEqualTo( "changed" );
        assertThat( readFile( firstFile ) ).containsEntry( FirstProperty.NAME.getName(), "changed" );
        assertThat( readFile( secondFile ) ).doesNotContainKey( FirstProperty.NAME.getName() );
    }

    /**
     * Verifies that several values written together are persisted to the named file.
     */
    @Test
    void shouldPersistEveryValueToNamedFile_whenValuesAreWrittenTogether() throws IOException {
        // GIVEN
        Path firstFile = resolveConfigurationFile( FIRST_FILE );
        underTest.loadFile( FIRST_FILE, firstDefaults() );
        SystemPropertyValues values = SystemPropertyValues.create()
                .with( FirstProperty.NAME, "changed" )
                .with( FirstProperty.NOTE, "note" );

        // WHEN
        underTest.setValues( FIRST_FILE, values );

        // THEN
        assertThat( readFile( firstFile ) )
                .containsEntry( FirstProperty.NAME.getName(), "changed" )
                .containsEntry( FirstProperty.NOTE.getName(), "note" );
    }

    /**
     * Verifies that a setting of one file cannot be read through the name of another loaded file.
     */
    @Test
    void shouldThrowException_whenPropertyBelongsToAnotherFile() {
        // GIVEN
        loadBothFiles();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( SECOND_FILE, FirstProperty.NAME ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalArgumentException.class )
                .hasMessageContaining( "is not managed by file" );
    }

    /**
     * Verifies that loading the same file twice is rejected, because a second handler would diverge from
     * the first one.
     */
    @Test
    void shouldThrowException_whenFileIsLoadedTwice() {
        // GIVEN
        resolveConfigurationFile( FIRST_FILE );
        underTest.loadFile( FIRST_FILE, firstDefaults() );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.loadFile( FIRST_FILE, firstDefaults() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "has already been loaded" );
    }

    /**
     * Verifies that a file name rejected by the path service aborts loading and leaves the file unloaded.
     */
    @Test
    void shouldPropagateException_whenPathServiceRejectsFileName() {
        // GIVEN
        String fileName = "../outside.properties";
        given( pathService.getConfigurationFile( fileName ) ).willThrow( new PathResolutionException( "rejected" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.loadFile( fileName, firstDefaults() ) );

        // THEN
        assertThat( thrown ).isInstanceOf( PathResolutionException.class );
        assertThat( catchThrowable( () -> underTest.getValue( fileName, FirstProperty.NAME ) ) )
                .isInstanceOf( IllegalStateException.class );
    }

    /**
     * Verifies that reading from a file that has not been loaded is reported as a programming error.
     */
    @Test
    void shouldThrowException_whenReadFileIsNotLoaded() {
        // GIVEN
        // no file loaded

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( FIRST_FILE, FirstProperty.NAME ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "has not been loaded" );
    }

    /**
     * Verifies that writing to a file that has not been loaded is reported as a programming error.
     */
    @Test
    void shouldThrowException_whenWrittenFileIsNotLoaded() {
        // GIVEN
        // no file loaded

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( FIRST_FILE, FirstProperty.NAME, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "has not been loaded" );
    }

    /**
     * Verifies that a batch written to a file that has not been loaded is reported as a programming error.
     */
    @Test
    void shouldThrowException_whenBatchFileIsNotLoaded() {
        // GIVEN
        SystemPropertyValues values = SystemPropertyValues.create().with( FirstProperty.NAME, "value" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( FIRST_FILE, values ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( IllegalStateException.class )
                .hasMessageContaining( "has not been loaded" );
    }

    /**
     * Verifies that a null path service is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenPathServiceIsNull() {
        // GIVEN
        // no path service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesServiceImpl( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "pathService must not be null" );
    }

    /**
     * Verifies that loading a file with a null name is rejected before the path is resolved.
     */
    @Test
    void shouldThrowException_whenLoadedFileNameIsNull() {
        // GIVEN
        // no file name at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.loadFile( null, firstDefaults() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "fileName must not be null" );
    }

    /**
     * Verifies that loading a file with null default values is rejected before the path is resolved.
     */
    @Test
    void shouldThrowException_whenLoadedDefaultValuesAreNull() {
        // GIVEN
        // no default values at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.loadFile( FIRST_FILE, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "defaultValues must not be null" );
    }

    /**
     * Verifies that reading with a null file name is rejected before any lookup.
     */
    @Test
    void shouldThrowException_whenReadFileNameIsNull() {
        // GIVEN
        // no file name at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( null, FirstProperty.NAME ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "fileName must not be null" );
    }

    /**
     * Verifies that reading a null setting of a loaded file is rejected.
     */
    @Test
    void shouldThrowException_whenReadPropertyIsNull() {
        // GIVEN
        loadBothFiles();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getValue( FIRST_FILE, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "property must not be null" );
    }

    /**
     * Verifies that writing with a null file name is rejected before any lookup.
     */
    @Test
    void shouldThrowException_whenWrittenFileNameIsNull() {
        // GIVEN
        // no file name at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( null, FirstProperty.NAME, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "fileName must not be null" );
    }

    /**
     * Verifies that writing a null setting of a loaded file is rejected before the file is touched.
     */
    @Test
    void shouldThrowException_whenWrittenPropertyIsNull() {
        // GIVEN
        loadBothFiles();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( FIRST_FILE, null, "value" ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "property must not be null" );
    }

    /**
     * Verifies that writing a null value to a loaded file is rejected before the file is touched.
     */
    @Test
    void shouldThrowException_whenWrittenValueIsNull() {
        // GIVEN
        loadBothFiles();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValue( FIRST_FILE, FirstProperty.NAME, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "value must not be null" );
    }

    /**
     * Verifies that a batch with a null file name is rejected before any lookup.
     */
    @Test
    void shouldThrowException_whenBatchFileNameIsNull() {
        // GIVEN
        SystemPropertyValues values = SystemPropertyValues.create().with( FirstProperty.NAME, "value" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( null, values ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "fileName must not be null" );
    }

    /**
     * Verifies that a null batch written to a loaded file is rejected before the file is touched.
     */
    @Test
    void shouldThrowException_whenBatchValuesAreNull() {
        // GIVEN
        loadBothFiles();

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.setValues( FIRST_FILE, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "values must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private Path resolveConfigurationFile( String fileName ) {
        Path file = directory.resolve( ".vDeck" ).resolve( "config" ).resolve( fileName );
        given( pathService.getConfigurationFile( fileName ) ).willReturn( file );
        return file;
    }

    private void loadBothFiles() {
        resolveConfigurationFile( FIRST_FILE );
        resolveConfigurationFile( SECOND_FILE );
        underTest.loadFile( FIRST_FILE, firstDefaults() );
        underTest.loadFile( SECOND_FILE, secondDefaults() );
    }

    private static SystemPropertyValues firstDefaults() {
        return SystemPropertyValues.create()
                .with( FirstProperty.NAME, "first-default" )
                .with( FirstProperty.NOTE, "" );
    }

    private static SystemPropertyValues secondDefaults() {
        return SystemPropertyValues.create().with( SecondProperty.PORT, "2456" );
    }

    private static Properties readFile( Path file ) throws IOException {
        Properties properties = new Properties();
        try ( BufferedReader reader = Files.newBufferedReader( file, StandardCharsets.UTF_8 ) ) {
            properties.load( reader );
        }
        return properties;
    }

    /**
     * Test-local settings owned by the first file.
     */
    private enum FirstProperty implements SystemPropertyIf {

        NAME( "first.name", true ),
        NOTE( "first.note", false );

        private final String name;

        private final boolean mandatory;

        FirstProperty( String name, boolean mandatory ) {
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
     * Test-local settings owned by the second file.
     */
    private enum SecondProperty implements SystemPropertyIf {

        PORT;

        @Override
        public @Nonnull String getName() {
            return "second.port";
        }

        @Override
        public boolean isMandatory() {
            return true;
        }
    }
}
