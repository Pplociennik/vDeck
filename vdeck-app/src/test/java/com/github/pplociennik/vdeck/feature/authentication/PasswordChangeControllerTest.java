package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.exc.PasswordChangeException;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies MVC behavior of {@link PasswordChangeController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class PasswordChangeControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private PanelUserService panelUserService;

    private PasswordChangeController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated panel-user service.
     */
    @BeforeEach
    void setUp() {
        underTest = new PasswordChangeController( panelUserService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that opening the page supplies an empty password request without exposing prior secrets.
     */
    @Test
    void shouldReturnEmptyPasswordForm_whenPageIsOpened() {
        // GIVEN
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showPasswordChange( model );

        // THEN
        assertThat( result ).isEqualTo( "authentication/change-password" );
        assertThat( model.get( "passwordChangeRequest" ) )
                .isEqualTo( new PasswordChangeRequest( null, null, null ) );
    }

    /**
     * Verifies that annotation validation failures end the change with the process exception without
     * calling password persistence.
     */
    @Test
    void shouldThrowInvalidInput_whenBindingHasErrors() {
        // GIVEN
        PasswordChangeRequest passwordRequest = new PasswordChangeRequest( "admin", "short", "short" );
        BindingResult bindingResult = bindingResult( passwordRequest );
        bindingResult.addError( new FieldError( "passwordChangeRequest", "newPassword", "too short" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword(
                passwordRequest,
                bindingResult,
                authentication(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse() ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( PasswordChangeMessage.INVALID_INPUT ) );
        verify( panelUserService, never() ).changePassword( ArgumentMatchers.any(), ArgumentMatchers.any() );
    }

    /**
     * Verifies that a successful replacement invalidates the authenticated session and returns to login.
     */
    @Test
    void shouldInvalidateSessionAndRedirectToLogin_whenPasswordChanges() {
        // GIVEN
        PasswordChangeRequest passwordRequest = validRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();

        // WHEN
        String result = underTest.changePassword( passwordRequest, bindingResult( passwordRequest ),
                authentication(), request, new MockHttpServletResponse() );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/login" );
        assertThat( request.getSession( false ) ).isNull();
        verify( panelUserService ).changePassword( "admin", passwordRequest );
    }

    /**
     * Verifies that a rejection by the service is left to the controller advice and keeps the session.
     */
    @Test
    void shouldPropagateRejectionAndKeepSession_whenPasswordChangeIsRejected() {
        // GIVEN
        PasswordChangeRequest passwordRequest = validRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        PasswordChangeException rejection = new PasswordChangeException( PasswordChangeMessage.CURRENT_PASSWORD_INCORRECT );
        willThrow( rejection ).given( panelUserService ).changePassword( "admin", passwordRequest );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changePassword( passwordRequest,
                bindingResult( passwordRequest ), authentication(), request, new MockHttpServletResponse() ) );

        // THEN
        assertThat( thrown ).isSameAs( rejection );
        assertThat( request.getSession( false ) ).isNotNull();
    }

    /**
     * Verifies that a null panel-user service is rejected by the constructor contract.
     */
    @Test
    void shouldThrowException_whenPanelUserServiceIsNull() {
        // GIVEN
        // no panel-user service

        // WHEN
        Throwable thrown = catchThrowable( () -> new PasswordChangeController( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "panelUserService must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private PasswordChangeRequest validRequest() {
        return new PasswordChangeRequest( "admin", "Replacement-1", "Replacement-1" );
    }

    private BindingResult bindingResult( PasswordChangeRequest passwordRequest ) {
        return new BeanPropertyBindingResult( passwordRequest, "passwordChangeRequest" );
    }

    private Authentication authentication() {
        return UsernamePasswordAuthenticationToken.authenticated( "admin", "<redacted>", List.of() );
    }
}
