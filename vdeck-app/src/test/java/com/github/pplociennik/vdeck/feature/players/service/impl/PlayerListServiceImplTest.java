package com.github.pplociennik.vdeck.feature.players.service.impl;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.players.PlayerListMessage;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerEntry;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerPlatform;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that {@link PlayerListServiceImpl} reads and changes the list files in the save directory of the
 * launch profile, keeping unrelated lines and refusing invalid changes before writing anything.
 */
@ExtendWith( MockitoExtension.class )
class PlayerListServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String SAVE_DIRECTORY = "valheim-data";

    private static final String STEAM_ID = "76561198012345678";

    private static final String STEAM_ENTRY = "Steam_" + STEAM_ID;

    @Mock
    private LaunchProfileService launchProfileService;

    @Mock
    private PathService pathService;

    @TempDir
    private Path installation;

    private Path saveDirectory;

    private PlayerListServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service whose save directory lies in an isolated installation directory.
     */
    @BeforeEach
    void setUp() {
        saveDirectory = installation.resolve( SAVE_DIRECTORY );
        underTest = new PlayerListServiceImpl( launchProfileService, pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that a list whose file does not exist yet is empty.
     */
    @Test
    void shouldReturnEmptyList_whenFileDoesNotExist() {
        // GIVEN
        givenSaveDirectory();

        // WHEN
        List< PlayerEntry > result = underTest.getEntries( PlayerList.BANNED );

        // THEN
        assertThat( result ).isEmpty();
    }

    /**
     * Verifies that the entries are read in file order from the list's own file, skipping comments and blank
     * lines.
     *
     * @throws IOException
     *         if the list file cannot be prepared
     */
    @Test
    void shouldReturnEntriesInFileOrder_whenFileHoldsEntriesAndComments() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "adminlist.txt", "// List admin players ID  ONE per line", "Xbox_2535412345678901", "",
                STEAM_ENTRY );

        // WHEN
        List< PlayerEntry > result = underTest.getEntries( PlayerList.ADMINS );

        // THEN
        assertThat( result ).containsExactly(
                new PlayerEntry( "Xbox_2535412345678901", PlayerPlatform.XBOX, "2535412345678901" ),
                new PlayerEntry( STEAM_ENTRY, PlayerPlatform.STEAM, STEAM_ID ) );
    }

    /**
     * Verifies that an unreadable list is reported as an I/O failure rather than shown as empty.
     *
     * @throws IOException
     *         if the directory cannot be prepared
     */
    @Test
    void shouldThrowUncheckedIoException_whenFileCannotBeRead() throws IOException {
        // GIVEN
        givenSaveDirectory();
        Files.createDirectories( saveDirectory.resolve( "bannedlist.txt" ) );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.getEntries( PlayerList.BANNED ) );

        // THEN
        assertThat( thrown ).isInstanceOf( UncheckedIOException.class ).hasMessageContaining( "bannedlist.txt" );
    }

    /**
     * Verifies that adding a player appends the prefixed entry and keeps the existing lines, comments
     * included.
     *
     * @throws IOException
     *         if the list file cannot be prepared or read
     */
    @Test
    void shouldAppendEntryAndKeepOtherLines_whenPlayerIsAdded() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "bannedlist.txt", "// List banned players ID  ONE per line", "Xbox_2535412345678901" );

        // WHEN
        underTest.addPlayer( PlayerList.BANNED, new PlayerEntryForm( PlayerPlatform.STEAM, STEAM_ID ) );

        // THEN
        assertThat( Files.readAllLines( saveDirectory.resolve( "bannedlist.txt" ) ) )
                .containsExactly( "// List banned players ID  ONE per line", "Xbox_2535412345678901", STEAM_ENTRY );
    }

    /**
     * Verifies that adding to a list without a file creates the save directory and the file.
     *
     * @throws IOException
     *         if the list file cannot be read
     */
    @Test
    void shouldCreateFile_whenPlayerIsAddedToMissingList() throws IOException {
        // GIVEN
        givenSaveDirectory();

        // WHEN
        underTest.addPlayer( PlayerList.PERMITTED, new PlayerEntryForm( PlayerPlatform.XBOX, "2535412345678901" ) );

        // THEN
        assertThat( Files.readAllLines( saveDirectory.resolve( "permittedlist.txt" ) ) )
                .containsExactly( "Xbox_2535412345678901" );
    }

    /**
     * Verifies that a form without a platform or user id is rejected before the file is touched.
     */
    @Test
    void shouldRejectWithoutTouchingFile_whenFormIsIncomplete() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( null, STEAM_ID );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( PlayerList.BANNED, form ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.INVALID_INPUT );
        assertThat( thrown.getSubmittedForm() ).isEqualTo( form );
        verifyNoInteractions( launchProfileService, pathService );
    }

    /**
     * Verifies that a user id not matching the chosen platform is rejected before the file is touched.
     */
    @Test
    void shouldRejectWithoutTouchingFile_whenUserIdDoesNotFitPlatform() {
        // GIVEN
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, "2535412345678901" );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( PlayerList.BANNED, form ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.INVALID_USER_ID );
        assertThat( thrown.isFailure() ).isFalse();
        verifyNoInteractions( launchProfileService, pathService );
    }

    /**
     * Verifies that a player already on the list is not added twice and the file stays unchanged.
     *
     * @throws IOException
     *         if the list file cannot be prepared or read
     */
    @Test
    void shouldRejectAndKeepFile_whenPlayerIsAlreadyListed() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "bannedlist.txt", "  " + STEAM_ENTRY );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( PlayerList.BANNED, new PlayerEntryForm( PlayerPlatform.STEAM, STEAM_ID ) ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.ALREADY_LISTED );
        assertThat( Files.readAllLines( saveDirectory.resolve( "bannedlist.txt" ) ) ).containsExactly( "  " + STEAM_ENTRY );
    }

    /**
     * Verifies that a save directory leaving the installation directory fails the addition with the save
     * failure message and its cause.
     */
    @Test
    void shouldFailWithCause_whenSaveDirectoryCannotBeResolved() {
        // GIVEN
        PathResolutionException cause = new PathResolutionException( "outside the installation" );
        given( launchProfileService.getProfile() ).willReturn( profile() );
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) ).willThrow( cause );
        PlayerEntryForm form = new PlayerEntryForm( PlayerPlatform.STEAM, STEAM_ID );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( PlayerList.ADMINS, form ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.SAVE_FAILED );
        assertThat( thrown.getSubmittedForm() ).isEqualTo( form );
        assertThat( thrown ).hasCause( cause );
    }

    /**
     * Verifies that an unreadable list fails the addition with the save failure message.
     *
     * @throws IOException
     *         if the directory cannot be prepared
     */
    @Test
    void shouldFailWithCause_whenFileCannotBeReadForAddition() throws IOException {
        // GIVEN
        givenSaveDirectory();
        Files.createDirectories( saveDirectory.resolve( "bannedlist.txt" ) );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.addPlayer( PlayerList.BANNED, new PlayerEntryForm( PlayerPlatform.STEAM, STEAM_ID ) ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.SAVE_FAILED );
        assertThat( thrown.getCause() ).isInstanceOf( IOException.class );
    }

    /**
     * Verifies that removing a player drops every line holding that entry and keeps the other lines.
     *
     * @throws IOException
     *         if the list file cannot be prepared or read
     */
    @Test
    void shouldRemoveEveryMatchingLineAndKeepOthers_whenPlayerIsRemoved() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "adminlist.txt", "// admins", STEAM_ENTRY, "Xbox_2535412345678901", " " + STEAM_ENTRY );

        // WHEN
        underTest.removePlayer( PlayerList.ADMINS, STEAM_ENTRY );

        // THEN
        assertThat( Files.readAllLines( saveDirectory.resolve( "adminlist.txt" ) ) )
                .containsExactly( "// admins", "Xbox_2535412345678901" );
    }

    /**
     * Verifies that an entry without a known platform prefix can be removed as it is shown.
     *
     * @throws IOException
     *         if the list file cannot be prepared or read
     */
    @Test
    void shouldRemoveEntry_whenEntryHasNoKnownPlatform() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "bannedlist.txt", STEAM_ID, STEAM_ENTRY );

        // WHEN
        underTest.removePlayer( PlayerList.BANNED, STEAM_ID );

        // THEN
        assertThat( Files.readAllLines( saveDirectory.resolve( "bannedlist.txt" ) ) ).containsExactly( STEAM_ENTRY );
    }

    /**
     * Verifies that removing a player who is not on the list is rejected and leaves the file unchanged, a
     * comment included even when it names the entry.
     *
     * @throws IOException
     *         if the list file cannot be prepared or read
     */
    @Test
    void shouldRejectAndKeepFile_whenPlayerIsNotListed() throws IOException {
        // GIVEN
        givenSaveDirectory();
        givenFile( "bannedlist.txt", "//" + STEAM_ENTRY );

        // WHEN
        PlayerListUpdateException thrown = catchThrowableOfType( PlayerListUpdateException.class,
                () -> underTest.removePlayer( PlayerList.BANNED, "//" + STEAM_ENTRY ) );

        // THEN
        assertThat( thrown.getMessageKey() ).isEqualTo( PlayerListMessage.NOT_LISTED );
        assertThat( thrown.getSubmittedForm() ).isNull();
        assertThat( Files.readAllLines( saveDirectory.resolve( "bannedlist.txt" ) ) ).containsExactly( "//" + STEAM_ENTRY );
    }

    /**
     * Verifies that missing arguments are rejected by every operation.
     */
    @Test
    void shouldThrowException_whenArgumentIsNull() {
        // GIVEN
        PlayerEntryForm form = PlayerEntryForm.empty();

        // WHEN
        Throwable entriesList = catchThrowable( () -> underTest.getEntries( null ) );
        Throwable addList = catchThrowable( () -> underTest.addPlayer( null, form ) );
        Throwable addForm = catchThrowable( () -> underTest.addPlayer( PlayerList.BANNED, null ) );
        Throwable removeList = catchThrowable( () -> underTest.removePlayer( null, STEAM_ENTRY ) );
        Throwable removeIdentifier = catchThrowable( () -> underTest.removePlayer( PlayerList.BANNED, null ) );

        // THEN
        assertThat( entriesList ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerList" );
        assertThat( addList ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerList" );
        assertThat( addForm ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "form" );
        assertThat( removeList ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "playerList" );
        assertThat( removeIdentifier ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "identifier" );
        verifyNoInteractions( launchProfileService, pathService );
    }

    /**
     * Verifies that the constructor refuses missing services.
     */
    @Test
    void shouldThrowException_whenServiceIsNull() {
        // GIVEN
        // no launch profile service or no path service

        // WHEN
        Throwable profile = catchThrowable( () -> new PlayerListServiceImpl( null, pathService ) );
        Throwable path = catchThrowable( () -> new PlayerListServiceImpl( launchProfileService, null ) );

        // THEN
        assertThat( profile ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "launchProfileService" );
        assertThat( path ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "pathService" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenSaveDirectory() {
        given( launchProfileService.getProfile() ).willReturn( profile() );
        given( pathService.getAbsolutePath( SAVE_DIRECTORY ) ).willReturn( saveDirectory );
    }

    private void givenFile( String fileName, String... lines ) throws IOException {
        Files.createDirectories( saveDirectory );
        Files.write( saveDirectory.resolve( fileName ), List.of( lines ), StandardCharsets.UTF_8 );
    }

    private static LaunchProfile profile() {
        return new LaunchProfile( "vDeck", 2456, "Dedicated", false, true, true, "", SAVE_DIRECTORY, 1800, 4,
                7200, 43200, null, Map.of(), Set.of() );
    }
}
