package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the merge of settings templates with the user's values (AD-025) and the layout-preserving update
 * of one settings file.
 */
class PropertiesMergerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String TEMPLATE = """
            # Panel settings.
            kept=default
            optional=
            # Added in the new version.
            added=new default
            """;

    @TempDir
    private Path directory;

    private PropertiesMerger underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the merger.
     */
    @BeforeEach
    void setUp() {
        underTest = new PropertiesMerger();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that common keys keep the user's values, new keys get defaults and removed keys disappear,
     * while order and comments come from the template.
     */
    @Test
    void shouldApplyMergeRules_whenUserFileDiffersFromTemplate() {
        // GIVEN
        String user = "kept=custom\nobsolete=old\n";

        // WHEN
        PropertiesMerger.MergeResult result = underTest.merge( TEMPLATE, user );

        // THEN
        assertThat( result.contents() ).isEqualTo( """
                # Panel settings.
                kept=custom
                optional=
                # Added in the new version.
                added=new default
                """ );
        assertThat( result.addedKeys() ).containsExactly( "optional", "added" );
        assertThat( result.removedKeys() ).containsExactly( "obsolete" );
    }

    /**
     * Verifies that an optional value the user deliberately left empty stays empty.
     */
    @Test
    void shouldKeepEmptyValue_whenUserClearedOptionalSetting() {
        // GIVEN
        String template = "optional=preset\n";
        String user = "optional=\n";

        // WHEN
        PropertiesMerger.MergeResult result = underTest.merge( template, user );

        // THEN
        assertThat( result.contents() ).isEqualTo( "optional=\n" );
        assertThat( result.addedKeys() ).isEmpty();
    }

    /**
     * Verifies that a missing user file yields the template unchanged.
     */
    @Test
    void shouldReturnTemplate_whenUserHasNoFile() {
        // GIVEN
        // No user file.

        // WHEN
        PropertiesMerger.MergeResult result = underTest.merge( TEMPLATE, null );

        // THEN
        assertThat( result.contents() ).isEqualTo( TEMPLATE );
        assertThat( result.removedKeys() ).isEmpty();
    }

    /**
     * Verifies that special and non-ASCII characters survive as ASCII escapes readable by Properties.
     */
    @Test
    void shouldEscapeValue_whenItContainsSpecialCharacters() throws Exception {
        // GIVEN
        String template = "server.password=\n";
        String value = " Zażółć=#!:\\x";
        Properties user = new Properties();
        user.setProperty( "server.password", value );
        var writer = new java.io.StringWriter();
        user.store( writer, null );

        // WHEN
        PropertiesMerger.MergeResult result = underTest.merge( template, writer.toString() );

        // THEN
        Properties read = new Properties();
        read.load( new StringReader( result.contents() ) );
        assertThat( read.getProperty( "server.password" ) ).isEqualTo( value );
        assertThat( result.contents().chars() ).allMatch( character -> character < 0x80 );
    }

    /**
     * Verifies that the template's Windows line endings and continuation lines are preserved.
     */
    @Test
    void shouldPreserveTemplateLines_whenTemplateUsesCrlfAndContinuations() {
        // GIVEN
        String template = "# c\r\nlong=first \\\r\n  second\r\nshort=1\r\n";

        // WHEN
        PropertiesMerger.MergeResult result = underTest.merge( template, "short=2\r\n" );

        // THEN
        assertThat( result.contents() ).isEqualTo( "# c\r\nlong=first \\\r\n  second\r\nshort=2\r\n" );
        assertThat( result.addedKeys() ).containsExactly( "long" );
    }

    /**
     * Verifies that the file variant writes the result and leaves both inputs untouched.
     */
    @Test
    void shouldWriteTarget_whenMergingFiles() throws Exception {
        // GIVEN
        Path template = Files.writeString( directory.resolve( "template.properties" ), TEMPLATE );
        Path user = Files.writeString( directory.resolve( "user.properties" ), "kept=mine\n" );
        Path target = directory.resolve( "merged/user.properties" );

        // WHEN
        underTest.mergeFiles( template, user, target );

        // THEN
        assertThat( Files.readString( target, StandardCharsets.UTF_8 ) ).contains( "kept=mine", "added=new default" );
        assertThat( user ).hasContent( "kept=mine\n" );
        assertThat( template ).hasContent( TEMPLATE );
    }

    /**
     * Verifies that a missing user file is merged as an empty one.
     */
    @Test
    void shouldUseTemplate_whenUserFileIsMissing() throws Exception {
        // GIVEN
        Path template = Files.writeString( directory.resolve( "template.properties" ), TEMPLATE );
        Path target = directory.resolve( "merged.properties" );

        // WHEN
        underTest.mergeFiles( template, directory.resolve( "missing.properties" ), target );

        // THEN
        assertThat( target ).hasContent( TEMPLATE );
    }

    /**
     * Verifies that a malformed user file stops the merge instead of losing settings.
     */
    @Test
    void shouldThrowException_whenUserFileIsMalformed() {
        // GIVEN
        String user = "broken=\\u12\n";

        // WHEN
        var failure = catchThrowable( () -> underTest.merge( TEMPLATE, user ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that an update replaces values in place and keeps comments, blank lines and key order.
     */
    @Test
    void shouldKeepLayout_whenUpdatingValues() {
        // GIVEN
        String text = "# ====\r\n# Section\r\n# ====\r\n\r\n# First.\r\nfirst=1\r\n\r\n# Second.\r\nsecond=2\r\n";
        Properties values = new Properties();
        values.setProperty( "second", "zwei" );
        values.setProperty( "first", "eins" );

        // WHEN
        String result = underTest.update( text, values );

        // THEN
        assertThat( result ).isEqualTo(
                "# ====\r\n# Section\r\n# ====\r\n\r\n# First.\r\nfirst=eins\r\n\r\n# Second.\r\nsecond=zwei\r\n" );
    }

    /**
     * Verifies that an update keeps a key without a new value and appends new keys in name order.
     */
    @Test
    void shouldAppendMissingKeys_whenTextLacksThem() {
        // GIVEN
        String text = "# Comment.\nkept=as is\n";
        Properties values = new Properties();
        values.setProperty( "zeta", "z" );
        values.setProperty( "alpha", "a" );

        // WHEN
        String result = underTest.update( text, values );

        // THEN
        assertThat( result ).isEqualTo( "# Comment.\nkept=as is\nalpha=a\nzeta=z\n" );
    }

    /**
     * Verifies that an update of an empty text writes only the given keys.
     */
    @Test
    void shouldWriteOnlyValues_whenTextIsEmpty() {
        // GIVEN
        Properties values = new Properties();
        values.setProperty( "key", "Zażółć" );

        // WHEN
        String result = underTest.update( "", values );

        // THEN
        assertThat( result ).isEqualTo( "key=Za\\u017C\\u00F3\\u0142\\u0107\n" );
    }

    /**
     * Verifies that an update of a malformed text fails instead of losing settings.
     */
    @Test
    void shouldThrowException_whenUpdatedTextIsMalformed() {
        // GIVEN
        String text = "broken=\\u12\n";

        // WHEN
        var failure = catchThrowable( () -> underTest.update( text, new Properties() ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingTemplate = catchThrowable( () -> underTest.merge( null, "" ) );
        var missingTarget = catchThrowable( () -> underTest.mergeFiles( directory, directory, null ) );
        var missingText = catchThrowable( () -> underTest.update( null, new Properties() ) );
        var missingValues = catchThrowable( () -> underTest.update( "", null ) );

        // THEN
        assertThat( missingTemplate ).isInstanceOf( NullPointerException.class );
        assertThat( missingTarget ).isInstanceOf( NullPointerException.class );
        assertThat( missingText ).isInstanceOf( NullPointerException.class );
        assertThat( missingValues ).isInstanceOf( NullPointerException.class );
    }
}
