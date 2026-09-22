package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link SystemPropertiesInitializer} loads every properties file of the panel with the
 * defaults declared by its property enum.
 */
@ExtendWith( MockitoExtension.class )
class SystemPropertiesInitializerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private SystemPropertiesService systemPropertiesService;

    private SystemPropertiesInitializer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh initializer backed by an isolated properties service.
     */
    @BeforeEach
    void setUp() {
        underTest = new SystemPropertiesInitializer( systemPropertiesService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that both the panel settings and the launch profile are loaded with their own defaults.
     */
    @Test
    void shouldLoadEverySettingsFile_whenSingletonsAreInstantiated() {
        // GIVEN
        // an initializer with no file loaded yet

        // WHEN
        underTest.afterSingletonsInstantiated();

        // THEN
        verify( systemPropertiesService ).loadFile( VdeckProperty.FILE_NAME, VdeckProperty.defaults() );
        verify( systemPropertiesService ).loadFile( LaunchProperty.FILE_NAME, LaunchProperty.defaults() );
    }

    /**
     * Verifies that a file that cannot be loaded aborts initialization instead of being skipped.
     */
    @Test
    void shouldPropagateException_whenFileCannotBeLoaded() {
        // GIVEN
        UncheckedIOException failure = new UncheckedIOException( "unreadable", new IOException( "unreadable" ) );
        willThrow( failure ).given( systemPropertiesService ).loadFile( eq( VdeckProperty.FILE_NAME ), any() );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.afterSingletonsInstantiated() );

        // THEN
        assertThat( thrown ).isSameAs( failure );
        verify( systemPropertiesService, never() ).loadFile( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that the constructor rejects a missing properties service.
     */
    @Test
    void shouldThrowException_whenPropertiesServiceIsNull() {
        // GIVEN
        // no properties service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new SystemPropertiesInitializer( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService must not be null" );
    }
}
