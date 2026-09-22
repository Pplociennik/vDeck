package com.github.pplociennik.vdeck.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies that {@link DefaultExceptionHandler} answers an unhandled exception with {@code 500} without
 * revealing it.
 */
class DefaultExceptionHandlerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private DefaultExceptionHandler underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh advice before each test.
     */
    @BeforeEach
    void setUp() {
        underTest = new DefaultExceptionHandler();
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that an exception no feature handles is answered with {@code 500} and no message.
     *
     * @throws IOException
     *         if the mock response cannot send the error
     */
    @Test
    void shouldSendServerErrorWithoutMessage_whenExceptionIsUnhandled() throws IOException {
        // GIVEN
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException exception = new UncheckedIOException( "disk full at C:\\secret", new IOException() );

        // WHEN
        underTest.handleUnexpected( exception, new MockHttpServletRequest( "POST", "/game-settings/advanced" ),
                response );

        // THEN
        assertThat( response.getStatus() ).isEqualTo( HttpStatus.INTERNAL_SERVER_ERROR.value() );
        assertThat( response.getErrorMessage() ).isNull();
        assertThat( response.getContentAsString() ).isEmpty();
    }

    /**
     * Verifies that the advice refuses to run without the exception it answers.
     */
    @Test
    void shouldThrowException_whenExceptionIsNull() {
        // GIVEN
        MockHttpServletRequest request = new MockHttpServletRequest( "GET", "/" );

        // WHEN
        Throwable thrown = catchThrowable(
                () -> underTest.handleUnexpected( null, request, new MockHttpServletResponse() ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "exception must not be null" );
    }
}
