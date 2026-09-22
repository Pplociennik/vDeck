package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.dto.InitialPasswordChangeRequest;
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
 * Verifies MVC behavior of {@link InitialPasswordChangeController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class InitialPasswordChangeControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @Mock
    private PanelUserService panelUserService;

    private InitialPasswordChangeController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated panel-user service.
     */
    @BeforeEach
    void setUp() {
        underTest = new InitialPasswordChangeController( panelUserService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that opening the page supplies an empty request holding no current-password field at all.
     */
    @Test
    void shouldReturnEmptyPasswordForm_whenPageIsOpened() {
        // GIVEN
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showInitialPasswordChange( model );

        // THEN
        assertThat( result ).isEqualTo( "authentication/initial-password-change" );
        assertThat( model.get( "initialPasswordChangeRequest" ) )
                .isEqualTo( new InitialPasswordChangeRequest( null, null ) );
    }

    /**
     * Verifies that annotation validation failures end the change with the process exception without
     * calling password persistence.
     */
    @Test
    void shouldThrowInvalidInput_whenBindingHasErrors() {
        // GIVEN
        InitialPasswordChangeRequest passwordRequest = new InitialPasswordChangeRequest( "short", "short" );
        BindingResult bindingResult = bindingResult( passwordRequest );
        bindingResult.addError( new FieldError( "initialPasswordChangeRequest", "newPassword", "too short" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword(
                passwordRequest,
                bindingResult,
                authentication(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse() ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( PasswordChangeException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( PasswordChangeMessage.INVALID_INPUT ) );
        verify( panelUserService, never() ).changeInitialPassword( ArgumentMatchers.any(), ArgumentMatchers.any() );
    }

    /**
     * Verifies that a successful replacement invalidates the first-login session and returns to login,
     * so the panel can only be entered with the password just chosen.
     */
    @Test
    void shouldInvalidateSessionAndRedirectToLogin_whenInitialPasswordChanges() {
        // GIVEN
        InitialPasswordChangeRequest passwordRequest = validRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();

        // WHEN
        String result = underTest.changeInitialPassword( passwordRequest, bindingResult( passwordRequest ),
                authentication(), request, new MockHttpServletResponse() );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/login" );
        assertThat( request.getSession( false ) ).isNull();
        verify( panelUserService ).changeInitialPassword( "admin", passwordRequest );
    }

    /**
     * Verifies that a rejection by the service is left to the controller advice and keeps the session.
     */
    @Test
    void shouldPropagateRejectionAndKeepSession_whenInitialPasswordChangeIsRejected() {
        // GIVEN
        InitialPasswordChangeRequest passwordRequest = validRequest();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        PasswordChangeException rejection = new PasswordChangeException( PasswordChangeMessage.NEW_PASSWORD_TOO_WEAK );
        willThrow( rejection ).given( panelUserService ).changeInitialPassword( "admin", passwordRequest );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.changeInitialPassword( passwordRequest,
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
        Throwable thrown = catchThrowable( () -> new InitialPasswordChangeController( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "panelUserService must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private InitialPasswordChangeRequest validRequest() {
        return new InitialPasswordChangeRequest( "Replacement-1", "Replacement-1" );
    }

    private BindingResult bindingResult( InitialPasswordChangeRequest passwordRequest ) {
        return new BeanPropertyBindingResult( passwordRequest, "initialPasswordChangeRequest" );
    }

    private Authentication authentication() {
        return UsernamePasswordAuthenticationToken.authenticated( "admin", "<redacted>", List.of() );
    }
}
