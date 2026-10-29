package com.github.pplociennik.vdeck.feature.players;

import com.github.pplociennik.vdeck.feature.players.domain.PlayerEntry;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import com.github.pplociennik.vdeck.feature.players.service.PlayerListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies MVC behavior of {@link PlayerListController} without starting a Spring context.
 */
@ExtendWith( MockitoExtension.class )
class PlayerListControllerTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String VIEW = "players/player-list";

    private static final String STEAM_ENTRY = "Steam_76561198012345678";

    @Mock
    private PlayerListService playerListService;

    private PlayerListController underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh controller with an isolated player list service.
     */
    @BeforeEach
    void setUp() {
        underTest = new PlayerListController( playerListService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that opening a panel publishes the list named by the address, its entries, the platforms and
     * an empty form.
     */
    @Test
    void shouldPublishListEntriesAndEmptyForm_whenPanelIsOpened() {
        // GIVEN
        List< PlayerEntry > entries = List.of( PlayerEntry.of( PlayerPlatform.STEAM, "76561198012345678" ) );
        given( playerListService.getEntries( PlayerList.ADMINS ) ).willReturn( entries );
        ExtendedModelMap model = new ExtendedModelMap();

        // WHEN
        String result = underTest.showList( "admins", model );

        // THEN
        assertThat( result ).isEqualTo( VIEW );
        assertThat( model.get( "playerList" ) ).isEqualTo( PlayerList.ADMINS );
        assertThat( model.get( "entries" ) ).isEqualTo( entries );
        assertThat( model.get( "platforms" ) ).isEqualTo( PlayerPlatform.values() );
        assertThat( model.get( "playerEntryForm" ) ).isEqualTo( PlayerEntryForm.empty() );
    }

    /**
     * Verifies that the entry flashed after a rejected addition is shown instead of an empty form.
     */
    @Test
    void shouldKeepFlashedForm_whenPanelIsOpenedAfterRejection() {
        // GIVEN
        PlayerEntryForm flashed = new PlayerEntryForm( PlayerPlatform.XBOX, "123" );
        given( playerListService.getEntries( PlayerList.BANNED ) ).willReturn( List.of() );
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute( "playerEntryForm", flashed );

        // WHEN
        underTest.showList( "bans", model );

        // THEN
        assertThat( model.get( "playerEntryForm" ) ).isEqualTo( flashed );
    }

    /**
     * Verifies that a valid addition is passed to the service and redirects back with the confirmation.
     */
    @Test
    void shouldAddPlayerAndRedirectWithConfirmation_whenFormIsValid() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, "76561198012345678" );

        // WHEN
        String result = underTest.addPlayer( "permitted", form, bindingResult( form ) );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/players/permitted?added" );
        verify( playerListService ).addPlayer( PlayerList.PERMITTED, form );
    }

    /**
     * Verifies that a form failing annotation validation is rejected without reaching the service.
     */
    @Test
    void shouldRejectWithoutService_whenFormFailsValidation() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, "abc" );
        BindingResult bindingResult = bindingResult( form );
        bindingResult.addError( new FieldError( "playerEntryForm", "userId", "must match" ) );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( "bans", form, bindingResult ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.INVALID_INPUT );
        assertThat( thrown.getPlayerList() ).isEqualTo( PlayerList.BANNED );
        assertThat( thrown.getSubmittedForm() ).isEqualTo( form );
        verifyNoInteractions( playerListService );
    }

    /**
     * Verifies that a removal is passed to the service and redirects back with the confirmation.
     */
    @Test
    void shouldRemovePlayerAndRedirectWithConfirmation_whenRemovalIsSubmitted() {
        // GIVEN
        // a listed Steam player

        // WHEN
        String result = underTest.removePlayer( "bans", STEAM_ENTRY );

        // THEN
        assertThat( result ).isEqualTo( "redirect:/players/bans?removed" );
        verify( playerListService ).removePlayer( PlayerList.BANNED, STEAM_ENTRY );
    }

    /**
     * Verifies that missing arguments are rejected before the service is used.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        PlayerEntryForm form = PlayerEntryForm.empty();
        BindingResult bindingResult = bindingResult( form );

        // WHEN
        Throwable showList = catchThrowable( () -> underTest.showList( null, new ExtendedModelMap() ) );
        Throwable showModel = catchThrowable( () -> underTest.showList( "bans", null ) );
        Throwable addForm = catchThrowable( () -> underTest.addPlayer( "bans", null, bindingResult ) );
        Throwable addBinding = catchThrowable( () -> underTest.addPlayer( "bans", form, null ) );
        Throwable removeIdentifier = catchThrowable( () -> underTest.removePlayer( "bans", null ) );

        // THEN
        assertThat( showList ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "list" );
        assertThat( showModel ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "model" );
        assertThat( addForm ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerEntryForm" );
        assertThat( addBinding ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "bindingResult" );
        assertThat( removeIdentifier ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "identifier" );
        verifyNoInteractions( playerListService );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private static BindingResult bindingResult( PlayerEntryForm form ) {
        return new BeanPropertyBindingResult( form, "playerEntryForm" );
    }
}
