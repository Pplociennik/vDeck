package com.github.pplociennik.vdeck.feature.players;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import com.github.pplociennik.vdeck.feature.players.service.PlayerListService;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static java.util.Objects.requireNonNull;

/**
 * Renders and processes the three panels of the "Gracze" branch of the side menu - "Bany",
 * "Administratorzy" and "Biała lista" - each showing one player list of the Valheim server as a table and
 * letting the user add a player to it or remove one from it.
 *
 * <p>All panels share one view, which receives the list and shows its entries. The panels change only the
 * files; the server reads them when it starts, which the view states in a warning. A successful change
 * redirects back to the panel, so reloading the page never repeats the post. A rejected or failed change
 * ends with a {@link PlayerListUpdateException}, which {@link PlayerListExceptionHandler} answers by
 * redirecting back with the message and, for an addition, the submitted entry.
 */
@Controller
class PlayerListController {

    /**
     * Address of a panel; the last segment names the list and only the segments of {@link PlayerList} match,
     * so any other address answers 404.
     */
    private static final String LIST_PATH = "/players/{list:bans|admins|permitted}";

    /**
     * Address the form adding a player posts to.
     */
    private static final String ADD_PATH = LIST_PATH + "/add";

    /**
     * Address the button removing a player posts to.
     */
    private static final String REMOVE_PATH = LIST_PATH + "/remove";

    /**
     * View shared by all panels.
     */
    private static final String PLAYER_LIST_VIEW = "players/player-list";

    /**
     * Model attribute holding the list shown by the view.
     */
    private static final String PLAYER_LIST_ATTRIBUTE = "playerList";

    /**
     * Model attribute holding the entries of the list.
     */
    private static final String ENTRIES_ATTRIBUTE = "entries";

    /**
     * Model attribute holding the platforms offered by the form.
     */
    private static final String PLATFORMS_ATTRIBUTE = "platforms";

    /**
     * Model attribute holding the form adding a player.
     */
    private static final String FORM_ATTRIBUTE = "playerEntryForm";

    private final PlayerListService playerListService;

    /**
     * Creates the controller delegating reading and changing the lists to the player list service.
     *
     * @param playerListService
     *         service owning the player list files
     * @throws NullPointerException
     *         if the service is null
     */
    PlayerListController( @Nonnull PlayerListService playerListService ) {
        this.playerListService = requireNonNull( playerListService, "playerListService must not be null" );
    }

    /**
     * Displays the panel of a list with its entries and an empty form. After a rejected addition the model
     * already holds the submitted entry, flashed by {@link PlayerListExceptionHandler}, and it is shown
     * instead.
     *
     * @param list
     *         segment naming the list
     * @param model
     *         MVC model receiving the list, its entries, the platforms and the form
     * @return player list template name
     * @throws java.io.UncheckedIOException
     *         if the list file cannot be read
     */
    @GetMapping( LIST_PATH )
    @Nonnull
    String showList( @PathVariable( "list" ) @Nonnull String list, @Nonnull Model model ) {
        requireNonNull( list, "list must not be null" );
        requireNonNull( model, "model must not be null" );

        PlayerList playerList = PlayerList.fromPathSegment( list );
        model.addAttribute( PLAYER_LIST_ATTRIBUTE, playerList );
        model.addAttribute( ENTRIES_ATTRIBUTE, playerListService.getEntries( playerList ) );
        model.addAttribute( PLATFORMS_ATTRIBUTE, PlayerPlatform.values() );
        if ( !model.containsAttribute( FORM_ATTRIBUTE ) ) {
            model.addAttribute( FORM_ATTRIBUTE, PlayerEntryForm.empty() );
        }
        return PLAYER_LIST_VIEW;
    }

    /**
     * Validates the submitted player and adds them to the list.
     *
     * @param list
     *         segment naming the list
     * @param playerEntryForm
     *         submitted platform and user id
     * @param bindingResult
     *         validation result produced from the form annotations
     * @return redirect to the panel with the confirmation flag
     * @throws PlayerListUpdateException
     *         if the form fails annotation validation, a check of the service rejects it or the file cannot
     *         be read or written; nothing was stored
     */
    @PostMapping( ADD_PATH )
    @Nonnull
    String addPlayer( @PathVariable( "list" ) @Nonnull String list,
                      @Valid @ModelAttribute( FORM_ATTRIBUTE ) @Nonnull PlayerEntryForm playerEntryForm,
                      @Nonnull BindingResult bindingResult ) {
        requireNonNull( list, "list must not be null" );
        requireNonNull( playerEntryForm, "playerEntryForm must not be null" );
        requireNonNull( bindingResult, "bindingResult must not be null" );

        PlayerList playerList = PlayerList.fromPathSegment( list );
        if ( bindingResult.hasErrors() ) {
            throw PlayerListUpdateException.rejected( PlayerListMessage.INVALID_INPUT, playerList, playerEntryForm );
        }
        playerListService.addPlayer( playerList, playerEntryForm );
        return "redirect:" + playerList.getPath() + "?added";
    }

    /**
     * Removes a player from the list.
     *
     * @param list
     *         segment naming the list
     * @param identifier
     *         whole entry to remove, as shown on the list
     * @return redirect to the panel with the confirmation flag
     * @throws PlayerListUpdateException
     *         if the entry is not on the list or the file cannot be read or written; nothing was stored
     */
    @PostMapping( REMOVE_PATH )
    @Nonnull
    String removePlayer( @PathVariable( "list" ) @Nonnull String list,
                         @RequestParam( "identifier" ) @Nonnull String identifier ) {
        requireNonNull( list, "list must not be null" );
        requireNonNull( identifier, "identifier must not be null" );

        PlayerList playerList = PlayerList.fromPathSegment( list );
        playerListService.removePlayer( playerList, identifier );
        return "redirect:" + playerList.getPath() + "?removed";
    }
}
