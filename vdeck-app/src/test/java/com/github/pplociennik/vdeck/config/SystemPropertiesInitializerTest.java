package com.github.pplociennik.vdeck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;

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

    @Mock
    private LaunchProfileService launchProfileService;

    private SystemPropertiesInitializer underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh initializer backed by an isolated properties service.
     */
    @BeforeEach
    void setUp() {
        underTest = new SystemPropertiesInitializer( systemPropertiesService, launchProfileService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that typed configuration initialization cannot be omitted accidentally. */
    @Test
    void shouldRejectConstruction_whenLaunchProfileServiceIsNull() {
        // GIVEN
        // No profile owner was supplied.

        // WHEN
        var thrown = catchThrowable( () -> new SystemPropertiesInitializer( systemPropertiesService, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchProfileService" );
    }

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
        var order = org.mockito.Mockito.inOrder( systemPropertiesService, launchProfileService );
        order.verify( systemPropertiesService ).loadFile( VdeckProperty.FILE_NAME, VdeckProperty.defaults() );
        order.verify( systemPropertiesService ).loadFile( LaunchProperty.FILE_NAME, LaunchProperty.defaults() );
        order.verify( launchProfileService ).reload();
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
        verify( launchProfileService, never() ).reload();
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
        Throwable thrown = catchThrowable( () -> new SystemPropertiesInitializer( null, launchProfileService ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService must not be null" );
    }

    /** Verifies a failed launch-profile file load prevents publishing a partial startup configuration. */
    @Test
    void shouldSkipReload_whenSecondSettingsFileCannotBeLoaded() {
        // GIVEN
        var failure = new UncheckedIOException( new IOException( "Synthetic second-file failure" ) );
        doNothing().when( systemPropertiesService ).loadFile( eq( VdeckProperty.FILE_NAME ), any() );
        willThrow( failure ).given( systemPropertiesService ).loadFile( eq( LaunchProperty.FILE_NAME ), any() );

        // WHEN
        var thrown = catchThrowable( underTest::afterSingletonsInstantiated );

        // THEN
        assertThat( thrown ).isSameAs( failure );
        verify( systemPropertiesService ).loadFile( VdeckProperty.FILE_NAME, VdeckProperty.defaults() );
        verify( launchProfileService, never() ).reload();
    }
}
