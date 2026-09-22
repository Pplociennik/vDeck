package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link PasswordChangeExceptionHandler} redirects back to the submitted password screen
 * with only the message as a flash attribute.
 */
class PasswordChangeExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private PasswordChangeExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh advice before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new PasswordChangeExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a rejection on the on-demand screen returns to it with the message and nothing else.
     */
    @Test
    void shouldRedirectToChangePasswordWithMessage_whenChangeIsRejected() {
        // GIVEN
        PasswordChangeException exception = new PasswordChangeException( PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleRejectedChange( exception, post( "/change-password" ), redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/change-password" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsExactlyEntriesOf( Map.of( "passwordChangeErrorKey",
                        PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT.getKey() ) );
    }

    /**
     * Verifies that a rejection on the mandatory screen returns to that screen, not to the other one.
     */
    @Test
    void shouldRedirectToInitialPasswordChange_whenInitialChangeIsRejected() {
        // GIVEN
        PasswordChangeException exception = new PasswordChangeException( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK );
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // WHEN
        String result = underTest.handleRejectedChange( exception, post( "/initial-password-change" ),
                redirectAttributes );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/initial-password-change" );
        assertThat( Map.< String, Object >copyOf( redirectAttributes.getFlashAttributes() ) )
                .containsEntry( "passwordChangeErrorKey", PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK.getKey() );
    }

    /**
     * Verifies that the advice refuses to run without the exception it answers.
     */
    @Test
    void shouldThrowException_whenExceptionIsNull() {
        // GIVEN
        MockHttpServletRequest request = post( "/change-password" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.handleRejectedChange( null, request, new RedirectAttributesModelMap() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "exception must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static MockHttpServletRequest post( String path ) {
        return new MockHttpServletRequest( "POST", path );
    }
}
