package com.github.pplociennik.vdeck.shared.lock;

import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Verifies when the panel takes the installation lock over and that the application runs exactly once.
 */
@ExtendWith( MockitoExtension.class )
class InstallationLockServiceTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String[] ARGS = { "--server.port=8080" };

    @Mock
    private SpringApplication application;

    @Mock
    private ConfigurableApplicationContext context;

    private Properties properties;

    private InstallationLockService underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the service with empty system properties.
     */
    @BeforeEach
    void setUp() {
        properties = new Properties();
        underTest = new InstallationLockService( properties );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that without a handoff the application runs without a registered lock.
     */
    @Test
    void shouldRunWithoutLock_whenNoHandoffIsGiven() {
        // GIVEN
        given( application.run( ARGS ) ).willReturn( context );

        // WHEN
        ConfigurableApplicationContext started = underTest.runWithLock( application, ARGS );

        // THEN
        assertThat( started ).isSameAs( context );
        then( application ).should( never() ).addInitializers( any() );
    }

    /**
     * Verifies that a handoff without the installation directory stops the start before Spring runs.
     */
    @Test
    void shouldThrowException_whenHandoffHasNoInstallationDirectory() {
        // GIVEN
        properties.setProperty( UpdateLayout.PANEL_HANDOFF_PROPERTY, "operation" );

        // WHEN
        var failure = catchThrowable( () -> underTest.runWithLock( application, ARGS ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "directory" );
        then( application ).shouldHaveNoInteractions();
    }

    /**
     * Verifies that a failing start is reported to the caller unchanged.
     */
    @Test
    void shouldRethrowException_whenApplicationFailsToStart() {
        // GIVEN
        IllegalStateException cause = new IllegalStateException( "context failed" );
        given( application.run( ARGS ) ).willThrow( cause );

        // WHEN
        var failure = catchThrowable( () -> underTest.runWithLock( application, ARGS ) );

        // THEN
        assertThat( failure ).isSameAs( cause );
    }

    /**
     * Verifies that null arguments are rejected.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingProperties = catchThrowable( () -> new InstallationLockService( null ) );
        var missingApplication = catchThrowable( () -> underTest.runWithLock( null, ARGS ) );
        var missingArgs = catchThrowable( () -> underTest.runWithLock( application, null ) );

        // THEN
        assertThat( missingProperties ).isInstanceOf( NullPointerException.class );
        assertThat( missingApplication ).isInstanceOf( NullPointerException.class );
        assertThat( missingArgs ).isInstanceOf( NullPointerException.class );
    }
}
