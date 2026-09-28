package com.github.pplociennik.vdeck.feature.server.exc;

import com.github.pplociennik.vdeck.feature.server.ServerMessage;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Ends the server start process, whether it was refused by a rule or failed while being carried out
 * (AD-014). The feature's {@code @ControllerAdvice} is the only place that handles it; services do not
 * return rejection results and controllers do not catch.
 *
 * <p>The two outcomes are told apart by {@link #isFailure()}, which is what decides whether the advice
 * logs a rejection the administrator caused or a failure that deserves a stack trace:
 *
 * <ul>
 *   <li>{@linkplain #rejected(ServerMessage) rejected} — a check refused the request before anything
 *       happened. There is no cause, because nothing went wrong technically;</li>
 *   <li>{@linkplain #startFailed(Throwable) failed} — every check passed but the process could not be
 *       created. The original failure is kept as the cause.</li>
 * </ul>
 *
 * <p>The exception never carries the launch profile or any argument, so the server password cannot
 * reach a log entry or a flash attribute through it.
 */
public final class ServerStartException extends RuntimeException {

    private final ServerMessage messageKey;

    private ServerStartException( @Nonnull ServerMessage messageKey, @Nullable Throwable cause ) {
        super( "Valheim server was not started: " + messageKey.name(), cause );
        this.messageKey = requireNonNull( messageKey, "messageKey must not be null" );
    }

    /**
     * Creates the exception ending a start that a check refused.
     *
     * @param messageKey
     *         reason to show the administrator
     * @return exception describing a rejection, without a cause
     * @throws NullPointerException
     *         if {@code messageKey} is {@code null}
     */
    public static @Nonnull ServerStartException rejected( @Nonnull ServerMessage messageKey ) {
        return new ServerStartException( requireNonNull( messageKey, "messageKey must not be null" ), null );
    }

    /**
     * Creates the exception ending a start that failed while the process was being created.
     *
     * @param cause
     *         failure that prevented the launch
     * @return exception describing a failure, keeping its cause
     * @throws NullPointerException
     *         if {@code cause} is {@code null}
     */
    public static @Nonnull ServerStartException startFailed( @Nonnull Throwable cause ) {
        return new ServerStartException( ServerMessage.START_FAILED,
                requireNonNull( cause, "cause must not be null" ) );
    }

    /**
     * Returns the message to show the administrator.
     *
     * @return translation key of the reason this start did not happen
     */
    public @Nonnull ServerMessage getMessageKey() {
        return messageKey;
    }

    /**
     * Tells a technical failure apart from a rule rejection, which decides how loudly it is logged.
     *
     * @return {@code true} when the start failed technically, {@code false} when a check refused it
     */
    public boolean isFailure() {
        return getCause() != null;
    }
}