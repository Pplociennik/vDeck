package com.github.pplociennik.vdeck.runtime.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.api.WorldContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Instant;

/** Verifies that durable launch contexts carry complete, bounded and secret-free identity data. */
class ManagedLaunchTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================
    private ManagedLaunch underTest;

    private ProcessIdentity identity;

    private WorldContext world;

    private Path log;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================
    /** Creates an independent context without reading files or inspecting real processes. */
    @BeforeEach
    void setUp() {
        Path root = Path.of( "fixture" ).toAbsolutePath().normalize();
        identity = new ProcessIdentity( 42, Instant.EPOCH, root.resolve( "valheim_server.exe" ) );
        world = new WorldContext( "Captured", root.resolve( "save" ), root.resolve( "backups" ) );
        log = root.resolve( "run.log" );
        underTest = new ManagedLaunch( "selected-launch", identity, world, log );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================
    /** The world context is the immutable selection captured for this launch. */
    @Test
    void shouldRetainCapturedWorld_whenContextIsCreated() {
        // GIVEN
        // A complete context.
        // WHEN
        var result = underTest.world();
        // THEN
        assertThat( result ).isSameAs( world );
        assertThat( underTest.identity() ).isSameAs( identity );
        assertThat( underTest.logFile() ).isEqualTo( log );
    }

    /**
     * Every required saved component rejects null explicitly.
     *
     * @param component
     *         missing saved component
     */
    @ParameterizedTest( name = "[{index}] missing {0}" )
    @ValueSource( strings = { "id", "identity", "world", "log" } )
    void shouldRejectComponent_whenNull( String component ) {
        // GIVEN
        // One missing component.
        // WHEN
        var failure = catchThrowable( () -> new ManagedLaunch( component.equals( "id" ) ? null : "selected-launch",
                component.equals( "identity" ) ? null : identity, component.equals( "world" ) ? null : world,
                component.equals( "log" ) ? null : log ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class );
    }

    /** Untrusted or oversized tokens cannot be reused as snapshot path components. */
    @Test
    void shouldRejectIdentifier_whenUnsafeOrOversized() {
        // GIVEN
        String oversized = "a".repeat( 101 );
        // WHEN
        var unsafe = catchThrowable( () -> new ManagedLaunch( "../launch", identity, world, log ) );
        var tooLong = catchThrowable( () -> new ManagedLaunch( oversized, identity, world, log ) );
        // THEN
        assertThat( unsafe ).isInstanceOf( IllegalArgumentException.class );
        assertThat( tooLong ).isInstanceOf( IllegalArgumentException.class );
    }

    /** Stored logs are installation-resolved absolute paths rather than working-directory guesses. */
    @Test
    void shouldRejectLog_whenRelative() {
        // GIVEN
        Path relative = Path.of( "run.log" );
        // WHEN
        var failure = catchThrowable( () -> new ManagedLaunch( "selected-launch", identity, world, relative ) );
        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }
}
