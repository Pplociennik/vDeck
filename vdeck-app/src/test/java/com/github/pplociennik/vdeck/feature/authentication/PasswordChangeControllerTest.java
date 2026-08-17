package com.github.pplociennik.vdeck.feature.authentication;

import com.github.pplociennik.vdeck.feature.authentication.domain.PasswordChangeResult;
import com.github.pplociennik.vdeck.feature.authentication.dto.PasswordChangeRequest;
import com.github.pplociennik.vdeck.feature.authentication.service.PanelUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
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
     * Verifies that annotation validation failures are rendered without calling password persistence.
     */
    @Test
    void shouldReturnValidationMessage_whenBindingHasErrors() {
        // GIVEN
        PasswordChangeRequest passwordRequest = new PasswordChangeRequest( "admin", "short", "short" );
        BindingResult bindingResult = bindingResult( passwordRequest );
        bindingResult.addError( new FieldError( "passwordChangeRequest", "newPassword", "too short" ) );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.changePassword(
                passwordRequest,
                bindingResult,
                authentication(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                model );

        // THEN
        assertThat( result ).isEqualTo( "authentication/change-password" );
        assertThat( model.get( "passwordChangeError" ).toString() ).contains( "12 and 128" );
        assertThat( model.get( "passwordChangeRequest" ) )
                .isEqualTo( new PasswordChangeRequest( null, null, null ) );
        verify( panelUserService, never() ).changePassword(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any() );
    }

    /**
     * Verifies that a successful replacement invalidates the authenticated session and returns to login.
     */
    @Test
    void shouldInvalidateSessionAndRedirectToLogin_whenPasswordChanges() {
        // GIVEN
        PasswordChangeRequest passwordRequest = validRequest();
        BindingResult bindingResult = bindingResult( passwordRequest );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = authentication();
        given( panelUserService.changePassword( "admin", passwordRequest ) )
                .willReturn( PasswordChangeResult.SUCCESS );

        // WHEN
        String result = underTest.changePassword(
                passwordRequest, bindingResult, authentication, request, response, new ExtendedModelMap() );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/login" );
        assertThat( request.getSession( false ) ).isNull();
    }

    /**
     * Verifies that a domain validation failure returns a safe message and an empty form.
     */
    @Test
    void shouldReturnSafeErrorAndEmptyForm_whenPasswordChangeIsRejected() {
        // GIVEN
        PasswordChangeRequest passwordRequest = validRequest();
        ExtendedModelMap model = new ExtendedModelMap();
        given( panelUserService.changePassword( "admin", passwordRequest ) )
                .willReturn( PasswordChangeResult.CURRENT_PASSWORD_INCORRECT );

        // WHEN
        String result = underTest.changePassword(
                passwordRequest,
                bindingResult( passwordRequest ),
                authentication(),
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                model );

        // THEN
        assertThat( result ).isEqualTo( "authentication/change-password" );
        assertThat( model.get( "passwordChangeError" ) ).isEqualTo( "The current password is incorrect." );
        assertThat( model.get( "passwordChangeRequest" ) )
                .isEqualTo( new PasswordChangeRequest( null, null, null ) );
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
        return new PasswordChangeRequest( "admin", "replacement-password", "replacement-password" );
    }

    private BindingResult bindingResult( PasswordChangeRequest passwordRequest ) {
        return new BeanPropertyBindingResult( passwordRequest, "passwordChangeRequest" );
    }

    private Authentication authentication() {
        return UsernamePasswordAuthenticationToken.authenticated( "admin", "<redacted>", java.util.List.of() );
    }
}
