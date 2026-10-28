package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.ServerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Verifies that each rendered view receives a fresh runtime snapshot and the configured server name. */
@ExtendWith( MockitoExtension.class )
class ServerStatusAdviceTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private ServerService serverService;

    @Mock
    private LaunchProfileService launchProfileService;

    @Mock
    private LaunchProfile launchProfile;

    private ServerStatusAdvice underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates fresh advice for each test. */
    @BeforeEach
    void setUp() {
        underTest = new ServerStatusAdvice( serverService, launchProfileService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that the view receives the service snapshot unchanged. */
    @Test
    void shouldReturnStatusFromService_whenViewRequestsStatus() {
        // GIVEN
        var expected = ServerStatus.stopped();
        given( serverService.getStatus() ).willReturn( expected );

        // WHEN
        var result = underTest.serverStatus();

        // THEN
        assertThat( result ).isSameAs( expected );
    }

    /** Verifies that a later failure replaces the earlier snapshot rather than being cached. */
    @Test
    void shouldReadStatusEveryTime_whenCalledTwice() {
        // GIVEN
        var first = ServerStatus.stopped();
        var second = ServerStatus.startFailed( null );
        given( serverService.getStatus() ).willReturn( first, second );

        // WHEN
        var firstResult = underTest.serverStatus();
        var secondResult = underTest.serverStatus();

        // THEN
        assertThat( firstResult ).isSameAs( first );
        assertThat( secondResult ).isSameAs( second );
        verify( serverService, times( 2 ) ).getStatus();
    }

    /** Verifies that the view receives the server name of the published launch profile. */
    @Test
    void shouldReturnServerNameFromProfile_whenViewRequestsName() {
        // GIVEN
        given( launchProfileService.getProfile() ).willReturn( launchProfile );
        given( launchProfile.serverName() ).willReturn( "Midgard" );

        // WHEN
        var result = underTest.serverName();

        // THEN
        assertThat( result ).isEqualTo( "Midgard" );
    }

    /** Verifies that advice cannot be constructed without a status source. */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // No service.

        // WHEN
        var thrown = catchThrowable( () -> new ServerStatusAdvice( null, launchProfileService ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "serverService" );
    }

    /** Verifies that advice cannot be constructed without a server name source. */
    @Test
    void shouldThrowException_whenLaunchProfileServiceIsNull() {
        // GIVEN
        // No launch profile service.

        // WHEN
        var thrown = catchThrowable( () -> new ServerStatusAdvice( serverService, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "launchProfileService" );
    }
}
