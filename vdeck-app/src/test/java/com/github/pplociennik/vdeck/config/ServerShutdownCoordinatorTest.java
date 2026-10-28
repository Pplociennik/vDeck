package com.github.pplociennik.vdeck.config;

import com.github.pplociennik.vdeck.feature.server.service.PanelCloseMode;
import com.github.pplociennik.vdeck.feature.server.service.PanelCloseStrategy;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

/**
 * Verifies that {@link ServerShutdownCoordinator} applies exactly the close strategy named by
 * {@code panel.close.strategy} while the panel shuts down.
 */
@ExtendWith( MockitoExtension.class )
class ServerShutdownCoordinatorTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private SystemPropertiesService systemPropertiesService;

    private List< PanelCloseMode > applied;

    private ServerShutdownCoordinator underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates the coordinator with one recording strategy for every mode.
     */
    @BeforeEach
    void setUp() {
        applied = new ArrayList<>();
        underTest = new ServerShutdownCoordinator( systemPropertiesService, List.of(
                recording( PanelCloseMode.STOP ), recording( PanelCloseMode.GRACEFUL ),
                recording( PanelCloseMode.LEAVE ) ) );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the strategy named by the stored setting, in any letter case, is the only one applied.
     *
     * @param value
     *         stored setting value
     * @param expected
     *         mode whose strategy must run
     */
    @ParameterizedTest( name = "[{index}] value=''{0}'' applies {1}" )
    @CsvSource( { "stop, STOP", "graceful, GRACEFUL", "leave, LEAVE", "Leave, LEAVE" } )
    void shouldApplyConfiguredStrategy_whenPanelShutsDown( String value, PanelCloseMode expected ) {
        // GIVEN
        givenSetting( value );

        // WHEN
        underTest.closeServer();

        // THEN
        assertThat( applied ).containsExactly( expected );
    }

    /**
     * Verifies that an unknown stored value falls back to the default strategy {@code stop}.
     */
    @Test
    void shouldApplyStopStrategy_whenSettingIsUnknown() {
        // GIVEN
        givenSetting( "kill" );

        // WHEN
        underTest.closeServer();

        // THEN
        assertThat( applied ).containsExactly( PanelCloseMode.STOP );
    }

    /**
     * Verifies that settings that cannot be read, for example after a failed start, fall back to {@code stop}.
     */
    @Test
    void shouldApplyStopStrategy_whenSettingCannotBeRead() {
        // GIVEN
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_CLOSE_STRATEGY ) )
                .willThrow( new IllegalStateException( "vDeck.properties is not loaded" ) );

        // WHEN
        underTest.closeServer();

        // THEN
        assertThat( applied ).containsExactly( PanelCloseMode.STOP );
    }

    /**
     * Verifies that a failing strategy does not break the remaining panel shutdown.
     */
    @Test
    void shouldNotThrow_whenStrategyFails() {
        // GIVEN
        underTest = new ServerShutdownCoordinator( systemPropertiesService, List.of(
                failing( PanelCloseMode.STOP ), recording( PanelCloseMode.GRACEFUL ),
                recording( PanelCloseMode.LEAVE ) ) );
        givenSetting( "stop" );

        // WHEN
        var failure = catchThrowable( () -> underTest.closeServer() );

        // THEN
        assertThat( failure ).isNull();
        assertThat( applied ).isEmpty();
    }

    /**
     * Verifies that a mode without a strategy is detected when the coordinator is created, not at shutdown.
     */
    @Test
    void shouldRejectStrategies_whenModeHasNoStrategy() {
        // GIVEN
        List< PanelCloseStrategy > strategies = List.of( recording( PanelCloseMode.STOP ),
                recording( PanelCloseMode.GRACEFUL ) );

        // WHEN
        var failure = catchThrowable( () -> new ServerShutdownCoordinator( systemPropertiesService, strategies ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "LEAVE" );
    }

    /**
     * Verifies that two strategies for one mode are rejected, because the choice would be ambiguous.
     */
    @Test
    void shouldRejectStrategies_whenModeHasTwoStrategies() {
        // GIVEN
        List< PanelCloseStrategy > strategies = List.of( recording( PanelCloseMode.STOP ),
                recording( PanelCloseMode.STOP ), recording( PanelCloseMode.GRACEFUL ),
                recording( PanelCloseMode.LEAVE ) );

        // WHEN
        var failure = catchThrowable( () -> new ServerShutdownCoordinator( systemPropertiesService, strategies ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalStateException.class ).hasMessageContaining( "STOP" );
    }

    /**
     * Verifies that missing dependencies are rejected when the coordinator is created.
     */
    @Test
    void shouldRejectNull_whenDependencyIsMissing() {
        // GIVEN
        List< PanelCloseStrategy > strategies = List.of();

        // WHEN
        var missingService = catchThrowable( () -> new ServerShutdownCoordinator( null, strategies ) );
        var missingStrategies = catchThrowable( () -> new ServerShutdownCoordinator( systemPropertiesService, null ) );

        // THEN
        assertThat( missingService ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService" );
        assertThat( missingStrategies ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "strategies" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenSetting( String value ) {
        given( systemPropertiesService.getValue( VdeckProperty.FILE_NAME, VdeckProperty.PANEL_CLOSE_STRATEGY ) )
                .willReturn( value );
    }

    private PanelCloseStrategy recording( PanelCloseMode mode ) {
        return new FakeStrategy( mode, () -> applied.add( mode ) );
    }

    private static PanelCloseStrategy failing( PanelCloseMode mode ) {
        return new FakeStrategy( mode, () -> {
            throw new IllegalStateException( "Synthetic strategy failure" );
        } );
    }

    /** Strategy fake running the given action instead of touching a server. */
    private record FakeStrategy( PanelCloseMode mode, Runnable action ) implements PanelCloseStrategy {

        @Override
        public PanelCloseMode getMode() {
            return mode;
        }

        @Override
        public void apply() {
            action.run();
        }
    }
}
