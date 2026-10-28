package com.github.pplociennik.vdeck.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the argument and environment checks of {@link SystemBrowser}. Opening a real browser starts a
 * system process and is therefore not covered by unit tests.
 */
class SystemBrowserTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private SystemBrowser underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a browser without a known Windows directory, so no test can start a process.
     */
    @BeforeEach
    void setUp() {
        underTest = new SystemBrowser( null );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a null address is rejected before any process is started.
     */
    @Test
    void shouldThrowException_whenAddressIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var exception = catchThrowable( () -> underTest.open( null ) );

        // THEN
        assertThat( exception )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "address" );
    }

    /**
     * Verifies that an unknown Windows directory fails with an I/O error instead of searching the PATH.
     */
    @Test
    void shouldThrowIoException_whenSystemRootIsUnknown() {
        // GIVEN
        // No additional setup.

        // WHEN
        var exception = catchThrowable( () -> underTest.open( URI.create( "http://127.0.0.1:8085" ) ) );

        // THEN
        assertThat( exception )
                .isInstanceOf( IOException.class )
                .hasMessageContaining( "SystemRoot" );
    }
}
