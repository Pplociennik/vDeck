package com.github.pplociennik.vdeck.feature.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.pplociennik.vdeck.feature.server.exc.ServerAdoptException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerKillException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerRestartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStartException;
import com.github.pplociennik.vdeck.feature.server.exc.ServerStopException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** Verifies translation of process exceptions into safe flash messages. */
class ServerExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private ServerExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /** Creates an independent exception handler. */
    @BeforeEach
    void setUp() {
        underTest = new ServerExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Adoption errors expose only the translation key when redirecting home. */
    @Test
    void shouldFlashAdoptionMessage_whenRecoveryFails() {
        // GIVEN
        var failure = new ServerAdoptException( ServerMessage.ADOPT_FAILED, new RuntimeException( "Technical" ) );
        var attributes = new RedirectAttributesModelMap();
        // WHEN
        var result = underTest.handleAdoptNotPerformed( failure, attributes );
        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) ).isEqualTo( "server.error.adoptFailed" );
        assertThat( attributes.getFlashAttributes() ).hasSize( 1 );
    }

    /** Adoption advice enforces its required input contract before storing any flash message. */
    @Test
    void shouldRejectMissingInput_whenAdoptionHandlerReceivesNull() {
        // GIVEN
        var rejection = new ServerAdoptException( ServerMessage.ADOPT_REJECTED, null );
        var attributes = new RedirectAttributesModelMap();
        // WHEN
        var exceptionFailure = catchThrowable( () -> underTest.handleAdoptNotPerformed( null, attributes ) );
        var attributesFailure = catchThrowable( () -> underTest.handleAdoptNotPerformed( rejection, null ) );
        // THEN
        assertThat( exceptionFailure ).isInstanceOf( NullPointerException.class );
        assertThat( attributesFailure ).isInstanceOf( NullPointerException.class );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** Verifies that a rejection carries its translation key across the redirect home. */
    @Test
    void shouldFlashReasonAndRedirect_whenStartRejected() {
        // GIVEN
        var rejection = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleStartNotPerformed( rejection, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.ALREADY_RUNNING.getKey() );
    }

    /** Verifies that a technical cause is not exposed to the browser. */
    @Test
    void shouldFlashReasonAndRedirect_whenStartFailed() {
        // GIVEN
        var failure = ServerStartException.startFailed( new IllegalStateException( "Technical failure" ) );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleStartNotPerformed( failure, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.START_FAILED.getKey() );
    }

    /** Verifies that missing exceptions are rejected before flash attributes are changed. */
    @Test
    void shouldThrowException_whenExceptionIsNull() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var thrown = catchThrowable( () -> underTest.handleStartNotPerformed( null, attributes ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "exception" );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** Verifies the non-null contract for flash storage. */
    @Test
    void shouldThrowException_whenRedirectAttributesAreNull() {
        // GIVEN
        var rejection = ServerStartException.rejected( ServerMessage.ALREADY_RUNNING );

        // WHEN
        var thrown = catchThrowable( () -> underTest.handleStartNotPerformed( rejection, null ) );

        // THEN
        assertThat( thrown ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "redirectAttributes" );
    }
    /** Kill validation returns to its confirmation screen without preserving force permission. */
    @Test
    void shouldReturnToConfirmation_whenSubmittedKillIsRejected() {
        // GIVEN
        var rejection = new ServerKillException(
                ServerMessage.CONFIRMATION_REQUIRED, null );
        var attributes = new RedirectAttributesModelMap();
        var request = new MockHttpServletRequest( "POST", "/server/kill" );
        request.setParameter( "launchId", "selected-launch" );
        // WHEN
        var result = underTest.handleKillNotPerformed( rejection, attributes, request );
        // THEN
        assertThat( result ).isEqualTo( "redirect:/server/kill-confirmation?launchId=selected-launch" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
    }

    /** Untrusted launch text is never inserted into a redirect target. */
    @Test
    void shouldReturnHome_whenKillIdentifierIsUnsafe() {
        // GIVEN
        var rejection = new ServerKillException(
                ServerMessage.KILL_REJECTED, new IllegalArgumentException( "Technical" ) );
        var attributes = new RedirectAttributesModelMap();
        var request = new MockHttpServletRequest( "POST", "/server/kill" );
        request.setParameter( "launchId", "unsafe?redirect=elsewhere" );
        // WHEN
        var result = underTest.handleKillNotPerformed( rejection, attributes, request );
        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
    }

    /** Failed Stop publishes its key without disclosing its technical cause. */
    @Test
    void shouldReturnHome_whenStopFailsAdmission() {
        // GIVEN
        var failure = new ServerStopException(
                ServerMessage.STOP_FAILED, new IllegalArgumentException( "Technical" ) );
        var attributes = new RedirectAttributesModelMap();
        // WHEN
        var result = underTest.handleStopNotPerformed( failure, attributes );
        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.STOP_FAILED.getKey() );
    }

    /** Every new advice boundary rejects null before publishing a flash. */
    @Test
    void shouldRejectNullBoundaries_whenControlAdviceIsCalled() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();
        var request = new MockHttpServletRequest();
        var stop = new ServerStopException( ServerMessage.STOP_REJECTED, null );
        var kill = new ServerKillException( ServerMessage.KILL_REJECTED, null );
        // WHEN
        var noStop = catchThrowable( () -> underTest.handleStopNotPerformed( null, attributes ) );
        var noStopFlash = catchThrowable( () -> underTest.handleStopNotPerformed( stop, null ) );
        var noKill = catchThrowable( () -> underTest.handleKillNotPerformed( null, attributes, request ) );
        var noKillFlash = catchThrowable( () -> underTest.handleKillNotPerformed( kill, null, request ) );
        var noRequest = catchThrowable( () -> underTest.handleKillNotPerformed( kill, attributes, null ) );
        // THEN
        assertThat( noStop ).isInstanceOf( NullPointerException.class );
        assertThat( noStopFlash ).isInstanceOf( NullPointerException.class );
        assertThat( noKill ).isInstanceOf( NullPointerException.class );
        assertThat( noKillFlash ).isInstanceOf( NullPointerException.class );
        assertThat( noRequest ).isInstanceOf( NullPointerException.class );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }

    /** A refused restart returns home with its own key, leaving the running server untouched. */
    @Test
    void shouldReturnHome_whenRestartIsRejected() {
        // GIVEN
        var rejection = new ServerRestartException( ServerMessage.RESTART_REJECTED, null );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleRestartNotPerformed( rejection, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.RESTART_REJECTED.getKey() );
    }

    /** A restart admission failure publishes its key without disclosing the technical cause. */
    @Test
    void shouldReturnHome_whenRestartFailsAdmission() {
        // GIVEN
        var failure = new ServerRestartException(
                ServerMessage.RESTART_FAILED, new IllegalArgumentException( "Technical" ) );
        var attributes = new RedirectAttributesModelMap();

        // WHEN
        var result = underTest.handleRestartNotPerformed( failure, attributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/" );
        assertThat( attributes.getFlashAttributes() ).containsOnlyKeys( "serverErrorKey" );
        assertThat( attributes.getFlashAttributes().get( "serverErrorKey" ) )
                .isEqualTo( ServerMessage.RESTART_FAILED.getKey() );
    }

    /** The restart advice rejects null before publishing a flash. */
    @Test
    void shouldRejectNull_whenRestartAdviceReceivesNull() {
        // GIVEN
        var attributes = new RedirectAttributesModelMap();
        var rejection = new ServerRestartException( ServerMessage.RESTART_REJECTED, null );

        // WHEN
        var noException = catchThrowable( () -> underTest.handleRestartNotPerformed( null, attributes ) );
        var noFlash = catchThrowable( () -> underTest.handleRestartNotPerformed( rejection, null ) );

        // THEN
        assertThat( noException ).isInstanceOf( NullPointerException.class );
        assertThat( noFlash ).isInstanceOf( NullPointerException.class );
        assertThat( attributes.getFlashAttributes() ).isEmpty();
    }


}
