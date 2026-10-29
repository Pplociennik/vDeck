package com.github.pplociennik.vdeck.feature.players.exc;

import com.github.pplociennik.vdeck.feature.players.PlayerListMessage;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Signals that a change of a player list was not stored, which ends the whole change. It is thrown both
 * when a check rejects the change and when the list file cannot be read or written, and it is answered by
 * the players feature's controller advice, which sends the user back to the list's panel with the message.
 *
 * <p>The exception carries the message shown to the user as a {@link PlayerListMessage}, never as a text,
 * the list that was changed, and - for an addition - the submitted form, so the panel can show the entry
 * again. A removal carries no form. A rejection has no cause; a failure keeps the failure that caused it.
 */
public class PlayerListUpdateException extends RuntimeException {

    private final PlayerListMessage messageKey;

    private final PlayerList playerList;

    private final PlayerEntryForm submittedForm;

    private PlayerListUpdateException( @Nonnull PlayerListMessage messageKey, @Nonnull PlayerList playerList,
                                       @Nullable PlayerEntryForm submittedForm, @Nullable Throwable cause ) {
        super( "Player list change was not stored: " + playerList.name() + " " + messageKey.name(), cause );
        this.messageKey = messageKey;
        this.playerList = playerList;
        this.submittedForm = submittedForm;
    }

    /**
     * Creates the exception for a change rejected by a check; nothing was stored.
     *
     * @param messageKey
     *         message explaining the rejection to the user
     * @param playerList
     *         list that was to be changed
     * @param form
     *         submitted entry of an addition, or {@code null} for a removal
     * @return exception without a cause
     * @throws NullPointerException
     *         if the message or the list is null
     */
    public static @Nonnull PlayerListUpdateException rejected( @Nonnull PlayerListMessage messageKey,
                                                               @Nonnull PlayerList playerList,
                                                               @Nullable PlayerEntryForm form ) {
        requireNonNull( messageKey, "messageKey must not be null" );
        requireNonNull( playerList, "playerList must not be null" );
        return new PlayerListUpdateException( messageKey, playerList, form, null );
    }

    /**
     * Creates the exception for a change that passed every check but whose file could not be read or
     * written.
     *
     * @param playerList
     *         list that was to be changed
     * @param form
     *         submitted entry of an addition, or {@code null} for a removal
     * @param cause
     *         failure of the file operation
     * @return exception carrying {@link PlayerListMessage#SAVE_FAILED} and the cause
     * @throws NullPointerException
     *         if the list or the cause is null
     */
    public static @Nonnull PlayerListUpdateException saveFailed( @Nonnull PlayerList playerList,
                                                                 @Nullable PlayerEntryForm form,
                                                                 @Nonnull Throwable cause ) {
        requireNonNull( playerList, "playerList must not be null" );
        requireNonNull( cause, "cause must not be null" );
        return new PlayerListUpdateException( PlayerListMessage.SAVE_FAILED, playerList, form, cause );
    }

    /**
     * Returns the message the panel has to show.
     *
     * @return translation key of the message explaining why nothing was stored
     */
    public @Nonnull PlayerListMessage getMessageKey() {
        return messageKey;
    }

    /**
     * Returns the list whose panel the user goes back to.
     *
     * @return list that was to be changed
     */
    public @Nonnull PlayerList getPlayerList() {
        return playerList;
    }

    /**
     * Returns the submitted entry to show again.
     *
     * @return submitted form of an addition, or {@code null} for a removal
     */
    public @Nullable PlayerEntryForm getSubmittedForm() {
        return submittedForm;
    }

    /**
     * Reports whether the change failed, rather than being rejected by a check.
     *
     * @return {@code true} when the exception has a cause, that is when the file could not be read or written
     */
    public boolean isFailure() {
        return getCause() != null;
    }
}
