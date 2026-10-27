package com.github.pplociennik.vdeck.runtime.installation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies bounded reads, durable writes, digests and tree deletion.
 */
class DurableFilesTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String HELLO_SHA_256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    private Path directory;

    private Path root;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Resolves the canonical temporary root.
     */
    @BeforeEach
    void setUp() throws Exception {
        root = directory.toRealPath();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that stored properties are read back with UTF-8 values and no temporary file remains.
     */
    @Test
    void shouldRoundTripProperties_whenWrittenDurably() throws Exception {
        // GIVEN
        Properties values = new Properties();
        values.setProperty( "name", "Zażółć" );
        Path target = root.resolve( "values.properties" );

        // WHEN
        DurableFiles.writeProperties( target, values, "comment" );
        Properties read = DurableFiles.readProperties( target, 1024 );

        // THEN
        assertThat( read ).containsEntry( "name", "Zażółć" );
        try ( var files = Files.list( root ) ) {
            assertThat( files ).containsExactly( target );
        }
    }

    /**
     * Verifies that a durable write replaces an existing file completely.
     */
    @Test
    void shouldReplaceContents_whenTargetExists() throws Exception {
        // GIVEN
        Path target = Files.writeString( root.resolve( "file.txt" ), "old and longer" );

        // WHEN
        DurableFiles.write( target, "new".getBytes( StandardCharsets.UTF_8 ) );

        // THEN
        assertThat( target ).hasContent( "new" );
    }

    /**
     * Verifies that a file above the limit is rejected.
     */
    @Test
    void shouldThrowException_whenPropertiesFileExceedsLimit() throws Exception {
        // GIVEN
        Path file = Files.writeString( root.resolve( "large.properties" ), "key=" + "x".repeat( 100 ) );

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.readProperties( file, 50 ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "size limit" );
    }

    /**
     * Verifies that a directory is not read as a properties file.
     */
    @Test
    void shouldThrowException_whenPropertiesPathIsNoRegularFile() {
        // GIVEN
        // The root is a directory.

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.readProperties( root, 50 ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that a copy returns the digest of the copied bytes and keeps the source.
     */
    @Test
    void shouldReturnDigest_whenCopying() throws Exception {
        // GIVEN
        Path source = Files.writeString( root.resolve( "source.txt" ), "hello" );
        Path target = root.resolve( "target.txt" );

        // WHEN
        String digest = DurableFiles.copyDurably( source, target );

        // THEN
        assertThat( digest ).isEqualTo( HELLO_SHA_256 );
        assertThat( target ).hasContent( "hello" );
        assertThat( source ).hasContent( "hello" );
    }

    /**
     * Verifies that a copy never overwrites an existing target.
     */
    @Test
    void shouldThrowException_whenCopyTargetExists() throws Exception {
        // GIVEN
        Path source = Files.writeString( root.resolve( "source.txt" ), "hello" );
        Path target = Files.writeString( root.resolve( "target.txt" ), "keep" );

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.copyDurably( source, target ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( target ).hasContent( "keep" );
    }

    /**
     * Verifies the SHA-256 digest of a known content.
     */
    @Test
    void shouldComputeKnownDigest_whenHashingFile() throws Exception {
        // GIVEN
        Path file = Files.writeString( root.resolve( "hello.txt" ), "hello" );

        // WHEN
        String digest = DurableFiles.sha256( file );

        // THEN
        assertThat( digest ).isEqualTo( HELLO_SHA_256 );
    }

    /**
     * Verifies that a whole tree below the trusted root is deleted and a missing tree is ignored.
     */
    @Test
    void shouldDeleteTree_whenBelowTrustedRoot() throws Exception {
        // GIVEN
        Path tree = Files.createDirectories( root.resolve( "tree/nested" ) ).getParent();
        Files.writeString( tree.resolve( "nested/file.txt" ), "x" );

        // WHEN
        DurableFiles.deleteTree( root, tree );
        DurableFiles.deleteTree( root, root.resolve( "missing" ) );

        // THEN
        assertThat( tree ).doesNotExist();
        assertThat( root ).exists();
    }

    /**
     * Verifies that the trusted root itself is never deleted.
     */
    @Test
    void shouldThrowException_whenDeletingTrustedRoot() {
        // GIVEN
        // The root exists.

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.deleteTree( root, root ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( root ).exists();
    }

    /**
     * Verifies that a tree outside the trusted root is refused.
     */
    @Test
    void shouldThrowException_whenTreeLeavesTrustedRoot() throws Exception {
        // GIVEN
        Path trusted = Files.createDirectory( root.resolve( "trusted" ) );
        Path outside = Files.createDirectory( root.resolve( "outside" ) );

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.deleteTree( trusted, outside ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
        assertThat( outside ).exists();
    }

    /**
     * Verifies that forcing a tree keeps every file and its contents unchanged.
     */
    @Test
    void shouldKeepContents_whenForcingTree() throws Exception {
        // GIVEN
        Path tree = Files.createDirectories( root.resolve( "tree/nested" ) ).getParent();
        Files.writeString( tree.resolve( "first.txt" ), "first" );
        Files.writeString( tree.resolve( "nested/second.txt" ), "second" );

        // WHEN
        DurableFiles.forceTree( tree );

        // THEN
        assertThat( tree.resolve( "first.txt" ) ).hasContent( "first" );
        assertThat( tree.resolve( "nested/second.txt" ) ).hasContent( "second" );
    }

    /**
     * Verifies that forcing a missing tree fails.
     */
    @Test
    void shouldThrowException_whenForcedTreeIsMissing() {
        // GIVEN
        Path missing = root.resolve( "missing" );

        // WHEN
        var failure = catchThrowable( () -> DurableFiles.forceTree( missing ) );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        Path file = root.resolve( "file" );

        // WHEN
        var missingFile = catchThrowable( () -> DurableFiles.sha256( null ) );
        var missingContents = catchThrowable( () -> DurableFiles.write( file, null ) );
        var missingTree = catchThrowable( () -> DurableFiles.deleteTree( root, null ) );
        var missingForcedTree = catchThrowable( () -> DurableFiles.forceTree( null ) );

        // THEN
        assertThat( missingFile ).isInstanceOf( NullPointerException.class );
        assertThat( missingContents ).isInstanceOf( NullPointerException.class );
        assertThat( missingTree ).isInstanceOf( NullPointerException.class );
        assertThat( missingForcedTree ).isInstanceOf( NullPointerException.class );
    }
}
