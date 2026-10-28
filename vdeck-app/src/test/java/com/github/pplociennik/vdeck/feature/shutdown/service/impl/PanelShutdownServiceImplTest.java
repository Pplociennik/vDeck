package com.github.pplociennik.vdeck.feature.shutdown.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PanelShutdownServiceImpl} hands the panel exit to its executor exactly once. The exit is
 * a recording action, so no test ends the test process.
 */
class PanelShutdownServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Tasks handed to the executor, kept instead of being run, so a test sees what was scheduled.
     */
    private final List< Runnable > scheduledTasks = new ArrayList<>();

    private final Executor recordingExecutor = scheduledTasks::add;

    private int exitCount;

    private PanelShutdownServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates a fresh service with a recording exit and executor before each test. */
    @BeforeEach
    void setUp() {
        scheduledTasks.clear();
        exitCount = 0;
        underTest = new PanelShutdownServiceImpl( () -> exitCount++, recordingExecutor );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that the first request schedules the exit on the executor instead of running it in place. */
    @Test
    void shouldScheduleExitOnExecutor_whenShutdownIsRequested() {
        // GIVEN
        // no earlier request

        // WHEN
        underTest.requestShutdown();

        // THEN
        assertThat( scheduledTasks ).hasSize( 1 );
        assertThat( exitCount ).isZero();
        scheduledTasks.getFirst().run();
        assertThat( exitCount ).isEqualTo( 1 );
    }

    /** Verifies that a repeated request, for example a second click, does not schedule a second exit. */
    @Test
    void shouldIgnoreRequest_whenShutdownWasAlreadyRequested() {
        // GIVEN
        underTest.requestShutdown();

        // WHEN
        underTest.requestShutdown();

        // THEN
        assertThat( scheduledTasks ).hasSize( 1 );
    }

    /** Verifies that the service cannot be created without the exit action. */
    @Test
    void shouldThrowException_whenPanelExitIsNull() {
        // GIVEN
        // no exit action

        // WHEN
        var exception = catchThrowable( () -> new PanelShutdownServiceImpl( null, recordingExecutor ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "panelExit" );
    }

    /** Verifies that the service cannot be created without the executor running the exit. */
    @Test
    void shouldThrowException_whenShutdownExecutorIsNull() {
        // GIVEN
        // no executor

        // WHEN
        var exception = catchThrowable( () -> new PanelShutdownServiceImpl( () -> exitCount++, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "shutdownExecutor" );
    }
}
