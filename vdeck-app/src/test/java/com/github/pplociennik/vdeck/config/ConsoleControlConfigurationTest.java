package com.github.pplociennik.vdeck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.github.pplociennik.vdeck.runtime.windows.JvmConsoleControlSender;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

/** Verifies sender composition is idle and respects the installation's path boundary. */
@ExtendWith( MockitoExtension.class )
class ConsoleControlConfigurationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private PathService paths;

    private ConsoleControlConfiguration underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates the composition root without starting Spring or a native process. */
    @BeforeEach
    void setUp() {
        underTest = new ConsoleControlConfiguration();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Creates an idle sender even before a runner artifact has been installed. */
    @Test
    void shouldCreateIdleSender_whenRunnerPathIsResolved() {
        // GIVEN
        given( paths.getAbsolutePath( "lib/vdeck-runner.jar" ) )
                .willReturn( Path.of( "installation with spaces", "lib", "vdeck-runner.jar" ).toAbsolutePath() );
        // WHEN
        var sender = underTest.consoleControlSender( paths );
        // THEN
        assertThat( sender ).isInstanceOf( JvmConsoleControlSender.class );
    }

    /** Preserves path validation failures instead of using the panel jar as a fallback. */
    @Test
    void shouldRejectComposition_whenRunnerPathIsRejected() {
        // GIVEN
        var expected = new IllegalArgumentException( "Synthetic unsafe installation path" );
        given( paths.getAbsolutePath( "lib/vdeck-runner.jar" ) ).willThrow( expected );
        // WHEN
        var failure = catchThrowable( () -> underTest.consoleControlSender( paths ) );
        // THEN
        assertThat( failure ).isSameAs( expected );
    }

    /** Rejects an absent resolver before reading JVM settings or composing a command. */
    @Test
    void shouldRejectComposition_whenPathsAreNull() {
        // GIVEN
        // Missing resolver.
        // WHEN
        var failure = catchThrowable( () -> underTest.consoleControlSender( null ) );
        // THEN
        assertThat( failure ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "paths" );
    }
}
