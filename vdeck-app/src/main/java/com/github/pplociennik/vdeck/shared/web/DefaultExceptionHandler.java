package com.github.pplociennik.vdeck.shared.web;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Answers every exception a controller does not hand to the controller advice of its own feature. The
 * main processes of the panel end with their own exception, answered by their feature's advice; any other
 * exception reaching the web layer is logged at {@code WARN} with its stack trace and answered with
 * {@code 500}, which Spring Boot renders as its error page. The response never carries the exception
 * message.
 *
 * <p>The advice has the lowest precedence, so every feature advice is consulted first.
 */
@Log4j2
@Order( Ordered.LOWEST_PRECEDENCE )
@ControllerAdvice
class DefaultExceptionHandler {

    /**
     * Logs an exception no feature handles and answers the request with {@code 500}.
     *
     * @param exception
     *         exception thrown while the request was handled
     * @param request
     *         request that failed, named in the log entry by its method and path
     * @param response
     *         response receiving the error status
     * @throws IOException
     *         if the error status cannot be sent
     */
    @ExceptionHandler( RuntimeException.class )
    void handleUnexpected( @Nonnull RuntimeException exception, @Nonnull HttpServletRequest request,
                           @Nonnull HttpServletResponse response ) throws IOException {
        requireNonNull( exception, "exception must not be null" );
        requireNonNull( request, "request must not be null" );
        requireNonNull( response, "response must not be null" );

        log.warn( "Request {} {} failed", request.getMethod(), request.getRequestURI(), exception );
        response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
    }
}
