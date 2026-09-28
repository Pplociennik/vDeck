package com.github.pplociennik.vdeck.feature.launch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that {@link LaunchProfileServiceImpl} maps the stored launch profile to its typed view,
 * validates submitted profiles and stores an accepted profile with a single write, without the server
 * password ever entering the web profile.
 */
@ExtendWith( MockitoExtension.class )
class LaunchProfileServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final Path BASE_DIRECTORY = Path.of( System.getProperty( "java.io.tmpdir" ) )
            .toAbsolutePath().resolve( "vdeck-installation" );

    @Mock
    private SystemPropertiesService systemPropertiesService;

    @Mock
    private PathService pathService;

    private Map< LaunchProperty, String > storedValues;

    private LaunchProfileServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a fresh service backed by an in-memory copy of the default launch profile file and an
     * installation directory that accepts every relative save directory.
     */
    @BeforeEach
    void setUp() {
        storedValues = new EnumMap<>( LaunchProperty.class );
        for ( LaunchProperty property : LaunchProperty.values() ) {
            storedValues.put( property, property.getDefaultValue() );
        }
        lenient().when( systemPropertiesService.getValue( eq( LaunchProperty.FILE_NAME ), any() ) )
                .thenAnswer( invocation -> storedValues.get( ( LaunchProperty ) invocation.getArgument( 1 ) ) );
        lenient().when( pathService.getBaseDirectory() ).thenReturn( BASE_DIRECTORY );
        lenient().when( pathService.getAbsolutePath( any() ) )
                .thenAnswer( invocation -> BASE_DIRECTORY.resolve( ( String ) invocation.getArgument( 0 ) ).normalize() );
        underTest = new LaunchProfileServiceImpl( systemPropertiesService, pathService );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** Verifies that profile reads reuse the published version without consulting raw settings. */
    @Test
    void shouldReuseConfiguration_whenSettingsHaveNotChanged() {
        // GIVEN
        underTest.reload();
        var configuration = underTest.getConfiguration();
        clearInvocations( systemPropertiesService );

        // WHEN
        var result = underTest.getConfiguration();
        var profile = underTest.getProfile();

        // THEN
        assertThat( result ).isSameAs( configuration );
        assertThat( profile ).isSameAs( configuration.profile() );
        verifyNoInteractions( systemPropertiesService );
    }

    /** Verifies that accessing settings before startup initialization reports a lifecycle error. */
    @Test
    void shouldRejectRead_whenConfigurationHasNotBeenLoaded() {
        // GIVEN
        // Startup has not published a configuration yet.

        // WHEN
        var thrown = catchThrowable( underTest::getConfiguration );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
    }

    /** Verifies that server edits replace the complete profile and password after persistence. */
    @Test
    void shouldPublishConfiguration_whenServerSettingsAreSaved() {
        // GIVEN
        underTest.reload();
        var previous = underTest.getConfiguration();
        givenSuccessfulWrite();

        // WHEN
        underTest.updateServerSettings( form( "Changed server", "ChangedSecret", false, "worlds" ) );

        // THEN
        var result = underTest.getConfiguration();
        assertThat( result ).isNotSameAs( previous );
        assertThat( result.profile().serverName() ).isEqualTo( "Changed server" );
        assertThat( result.password() ).isEqualTo( "ChangedSecret" );
        assertThat( result.profile().passwordSet() ).isTrue();
        assertThat( previous.profile().serverName() ).isEqualTo( "vDeck" );
        assertThat( previous.password() ).isEmpty();
    }

    /** Verifies that game edits refresh the cached world settings and preserve server credentials. */
    @Test
    void shouldRefreshGameSettings_whenGameFormIsSaved() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "UnchangedSecret" );
        underTest.reload();
        givenSuccessfulWrite();

        // WHEN
        underTest.updateGameSettings( gameForm( "hard", "veryhard" ) );

        // THEN
        var result = underTest.getConfiguration();
        assertThat( result.profile().preset() ).isEqualTo( WorldPreset.HARD );
        assertThat( result.profile().modifiers() ).containsEntry( WorldModifier.COMBAT, "veryhard" );
        assertThat( result.password() ).isEqualTo( "UnchangedSecret" );
    }

    /** Verifies that failed persistence never replaces the configuration available to Start. */
    @Test
    void shouldKeepConfiguration_whenSettingsCannotBeSaved() {
        // GIVEN
        underTest.reload();
        var previous = underTest.getConfiguration();
        willThrow( new UncheckedIOException( new IOException( "Unwritable" ) ) )
                .given( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );

        // WHEN
        var thrown = catchThrowable( () -> underTest.updateGameSettings( gameForm( "hard", "veryhard" ) ) );

        // THEN
        assertThat( thrown ).isInstanceOf( LaunchProfileUpdateException.class );
        assertThat( underTest.getConfiguration() ).isSameAs( previous );
    }

    /**
     * Verifies that concurrent Start reads keep the old complete version while a write is in progress.
     *
     * @throws Exception
     *         if the coordinated worker fails or exceeds its bounded wait
     */
    @Test
    void shouldPublishOnlyAfterPersistence_whenUpdateOverlapsRead() throws Exception {
        // GIVEN
        underTest.reload();
        var previous = underTest.getConfiguration();
        var writing = new CountDownLatch( 1 );
        var finishWrite = new CountDownLatch( 1 );
        doAnswer( invocation -> {
            writing.countDown();
            if ( !finishWrite.await( 5, TimeUnit.SECONDS ) ) {
                throw new IllegalStateException( "Timed out waiting to finish the test write" );
            }
            applyStoredValues( invocation.getArgument( 1 ) );
            return null;
        } ).when( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );

        try ( var executor = Executors.newSingleThreadExecutor() ) {
            // WHEN
            var update = executor.submit( () -> underTest.updateServerSettings(
                    form( "New server", "NewSecret", false, "worlds" ) ) );
            try {
                assertThat( writing.await( 5, TimeUnit.SECONDS ) ).isTrue();
                var duringWrite = underTest.getConfiguration();

                // THEN
                assertThat( duringWrite ).isSameAs( previous );
            } finally {
                finishWrite.countDown();
            }
            update.get( 5, TimeUnit.SECONDS );
            assertThat( underTest.getConfiguration().profile().serverName() ).isEqualTo( "New server" );
            assertThat( underTest.getConfiguration().password() ).isEqualTo( "NewSecret" );
        }
    }

    /**
     * Verifies that a file holding the defaults is mapped to the documented default profile, with no
     * password, no preset, no modifiers and no keys.
     */
    @Test
    void shouldReturnDefaultProfile_whenFileHoldsDefaults() {
        // GIVEN
        // the default values set up in setUp

        // WHEN
        underTest.reload();
        LaunchProfile result = underTest.getProfile();

        // THEN
        assertThat( result ).isEqualTo( new LaunchProfile( "vDeck", 2456, "Dedicated", false, true, false, "",
                "valheim-data", 1800, 4, 7200, 43200, null, Map.of(), Set.of() ) );
    }

    /**
     * Verifies that configured values are mapped to their typed form and that the password is reported
     * only as being set.
     */
    @Test
    void shouldReturnConfiguredProfile_whenFileHoldsCustomValues() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "secret-value" );
        storedValues.put( LaunchProperty.SERVER_PORT, "2500" );
        storedValues.put( LaunchProperty.SERVER_PUBLIC, "false" );
        storedValues.put( LaunchProperty.WORLD_PRESET, "hard" );
        storedValues.put( LaunchProperty.WORLD_MODIFIER_RAIDS, "none" );
        storedValues.put( LaunchProperty.WORLD_KEY_NO_MAP, "true" );

        // WHEN
        underTest.reload();
        LaunchProfile result = underTest.getProfile();

        // THEN
        assertThat( result.passwordSet() ).isTrue();
        assertThat( result.port() ).isEqualTo( 2500 );
        assertThat( result.publicServer() ).isFalse();
        assertThat( result.preset() ).isEqualTo( WorldPreset.HARD );
        assertThat( result.modifiers() ).containsExactly( Map.entry( WorldModifier.RAIDS, "none" ) );
        assertThat( result.enabledKeys() ).containsExactly( WorldKey.NO_MAP );
        assertThat( result.toString() ).doesNotContain( "secret-value" );
    }

    /**
     * Verifies that values broken by a manual edit of the file fall back to the defaults instead of
     * failing the screen.
     */
    @Test
    void shouldUseDefaults_whenStoredValuesAreMalformed() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PORT, "not-a-number" );
        storedValues.put( LaunchProperty.SERVER_CROSSPLAY, "yes" );
        storedValues.put( LaunchProperty.WORLD_PRESET, "legendary" );
        storedValues.put( LaunchProperty.WORLD_MODIFIER_COMBAT, "impossible" );

        // WHEN
        underTest.reload();
        LaunchProfile result = underTest.getProfile();

        // THEN
        assertThat( result.port() ).isEqualTo( 2456 );
        assertThat( result.crossplay() ).isFalse();
        assertThat( result.preset() ).isNull();
        assertThat( result.modifiers() ).isEmpty();
    }

    /**
     * Verifies that valid server settings are stored with a single write carrying only the server
     * settings, so the game settings keep their stored values.
     */
    @Test
    void shouldStoreOnlyServerSettingsInOneWrite_whenServerFormIsValid() {
        // GIVEN
        ServerSettingsForm form = form( "My server", "Secret1", false, "worlds/main" );

        // WHEN
        underTest.updateServerSettings( form );

        // THEN
        Map< SystemPropertyIf, String > written = capturedWrite();
        assertThat( written ).containsOnlyKeys( LaunchProperty.SERVER_NAME, LaunchProperty.SERVER_PORT,
                LaunchProperty.SERVER_WORLD, LaunchProperty.SERVER_PASSWORD, LaunchProperty.SERVER_PUBLIC,
                LaunchProperty.SERVER_CROSSPLAY, LaunchProperty.SERVER_INSTANCE_ID,
                LaunchProperty.SERVER_SAVE_DIRECTORY, LaunchProperty.SERVER_SAVE_INTERVAL,
                LaunchProperty.SERVER_BACKUP_COUNT, LaunchProperty.SERVER_BACKUP_SHORT,
                LaunchProperty.SERVER_BACKUP_LONG );
        assertThat( written )
                .containsEntry( LaunchProperty.SERVER_NAME, "My server" )
                .containsEntry( LaunchProperty.SERVER_PORT, "2457" )
                .containsEntry( LaunchProperty.SERVER_PASSWORD, "Secret1" )
                .containsEntry( LaunchProperty.SERVER_PUBLIC, "false" )
                .containsEntry( LaunchProperty.SERVER_CROSSPLAY, "true" )
                .containsEntry( LaunchProperty.SERVER_SAVE_DIRECTORY, "worlds/main" );
    }

    /**
     * Verifies that valid game settings are stored with a single write carrying only the world settings
     * in their command-line form, so the server settings keep their stored values.
     */
    @Test
    void shouldStoreOnlyGameSettingsInOneWrite_whenGameFormIsValid() {
        // GIVEN
        GameSettingsForm form = gameForm( "hard", "veryhard" );

        // WHEN
        underTest.updateGameSettings( form );

        // THEN
        Map< SystemPropertyIf, String > written = capturedWrite();
        assertThat( written ).hasSize( 10 ).doesNotContainKeys( LaunchProperty.SERVER_NAME,
                LaunchProperty.SERVER_PASSWORD, LaunchProperty.SERVER_SAVE_DIRECTORY );
        assertThat( written )
                .containsEntry( LaunchProperty.WORLD_PRESET, "hard" )
                .containsEntry( LaunchProperty.WORLD_MODIFIER_COMBAT, "veryhard" )
                .containsEntry( LaunchProperty.WORLD_MODIFIER_RAIDS, "" )
                .containsEntry( LaunchProperty.WORLD_KEY_NO_MAP, "true" )
                .containsEntry( LaunchProperty.WORLD_KEY_NO_BUILD_COST, "false" );
        verify( pathService, never() ).getAbsolutePath( any() );
    }

    /**
     * Verifies that the save directory is stored in its normalised form relative to the installation.
     */
    @Test
    void shouldStoreNormalisedSaveDirectory_whenPathContainsRedundantSegments() {
        // GIVEN
        ServerSettingsForm form = form( "My server", "", false, "./worlds/../worlds/main" );

        // WHEN
        underTest.updateServerSettings( form );

        // THEN
        assertThat( capturedWrite() ).containsEntry( LaunchProperty.SERVER_SAVE_DIRECTORY, "worlds/main" );
    }

    /**
     * Verifies that leaving the password field empty keeps the stored password.
     */
    @Test
    void shouldKeepStoredPassword_whenPasswordFieldIsEmpty() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "Stored1" );
        ServerSettingsForm form = form( "My server", "", false, "valheim-data" );

        // WHEN
        underTest.updateServerSettings( form );

        // THEN
        assertThat( capturedWrite() ).containsEntry( LaunchProperty.SERVER_PASSWORD, "Stored1" );
    }

    /**
     * Verifies that requesting the removal clears the stored password even when a new one was typed.
     */
    @Test
    void shouldClearPassword_whenRemovalIsRequested() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "Stored1" );
        ServerSettingsForm form = form( "My server", "Typed12", true, "valheim-data" );

        // WHEN
        underTest.updateServerSettings( form );

        // THEN
        assertThat( capturedWrite() ).containsEntry( LaunchProperty.SERVER_PASSWORD, "" );
    }

    /**
     * Verifies that a password contained in the server name is refused regardless of letter case,
     * because the Valheim server would refuse to start with it.
     */
    @Test
    void shouldRejectPassword_whenItIsPartOfServerName() {
        // GIVEN
        ServerSettingsForm form = form( "Vikings of the North", "viking", false, "valheim-data" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a kept stored password is checked against a changed server name as well.
     */
    @Test
    void shouldRejectKeptPassword_whenNewServerNameContainsIt() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "north" );
        ServerSettingsForm form = form( "Vikings of the North", "", false, "valheim-data" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a preset the server does not know is rejected and nothing is stored.
     */
    @Test
    void shouldRejectUpdate_whenPresetIsUnknown() {
        // GIVEN
        GameSettingsForm form = gameForm( "legendary", "" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a modifier value belonging to another modifier is rejected and nothing is stored.
     */
    @Test
    void shouldRejectUpdate_whenModifierValueIsNotAllowed() {
        // GIVEN
        GameSettingsForm form = gameForm( "", "most" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a form missing a required value is rejected, which protects callers that skip the
     * annotation validation.
     */
    @Test
    void shouldRejectUpdate_whenRequiredValueIsMissing() {
        // GIVEN
        ServerSettingsForm form = new ServerSettingsForm( "My server", null, "Dedicated", null, null, true, false,
                "", "valheim-data", 1800, 4, 7200, 43200 );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a save directory refused by the path service is reported and nothing is stored.
     */
    @Test
    void shouldRejectSaveDirectory_whenPathLeavesInstallation() {
        // GIVEN
        given( pathService.getAbsolutePath( "../outside" ) )
                .willThrow( new PathResolutionException( "resolves outside the installation directory" ) );
        ServerSettingsForm form = form( "My server", "", false, "../outside" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( LaunchProfileMessage.INVALID_SAVE_DIRECTORY ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that the installation directory itself is refused as the save directory, so worlds are
     * never mixed with the server and panel files.
     */
    @Test
    void shouldRejectSaveDirectory_whenItIsInstallationDirectory() {
        // GIVEN
        ServerSettingsForm form = form( "My server", "", false, "." );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( LaunchProfileMessage.INVALID_SAVE_DIRECTORY ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a failed write of the file ends the update with the process exception, which keeps
     * the write failure as its cause and the submitted values without the password.
     */
    @Test
    void shouldThrowSaveFailure_whenFileCannotBeWritten() {
        // GIVEN
        UncheckedIOException failure = new UncheckedIOException( "disk full", new IOException( "disk full" ) );
        willThrow( failure ).given( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
        ServerSettingsForm form = form( "My server", "Secret1", false, "valheim-data" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
            assertThat( exception.getCause() ).isSameAs( failure );
            assertThat( exception.getSubmittedForm() ).isEqualTo( form.withoutPassword() );
        } );
    }

    /**
     * Verifies that a failed write of the game settings ends the update with the process exception
     * keeping the write failure as its cause.
     */
    @Test
    void shouldThrowSaveFailure_whenGameSettingsCannotBeWritten() {
        // GIVEN
        UncheckedIOException failure = new UncheckedIOException( "disk full", new IOException( "disk full" ) );
        willThrow( failure ).given( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
        GameSettingsForm form = gameForm( "hard", "" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateGameSettings( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
            assertThat( exception.getCause() ).isSameAs( failure );
            assertThat( exception.getSubmittedForm() ).isEqualTo( form );
        } );
    }

    /**
     * Verifies that a null form is rejected before anything is read or written.
     */
    @Test
    void shouldThrowException_whenFormIsNull() {
        // GIVEN
        // no form at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerSettings( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "form must not be null" );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that the constructor rejects a missing properties service.
     */
    @Test
    void shouldThrowException_whenPropertiesServiceIsNull() {
        // GIVEN
        // no properties service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchProfileServiceImpl( null, pathService ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "systemPropertiesService must not be null" );
    }

    /**
     * Verifies that the constructor rejects a missing path service.
     */
    @Test
    void shouldThrowException_whenPathServiceIsNull() {
        // GIVEN
        // no path service at all

        // WHEN
        Throwable thrown = catchThrowable( () -> new LaunchProfileServiceImpl( systemPropertiesService, null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "pathService must not be null" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void givenSuccessfulWrite() {
        doAnswer( invocation -> {
            applyStoredValues( invocation.getArgument( 1 ) );
            return null;
        } ).when( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    private void applyStoredValues( SystemPropertyValues values ) {
        values.asMap().forEach( ( property, value ) -> storedValues.put( ( LaunchProperty ) property, value.strip() ) );
    }

    /**
     * Creates valid server settings varying only the fields the tests are about; the server is private,
     * runs on crossplay and uses port 2457.
     */
    private static ServerSettingsForm form( String serverName, String password, boolean clearPassword,
                                            String saveDirectory ) {
        return new ServerSettingsForm( serverName, 2457, "Dedicated", password, clearPassword, false, true, "",
                saveDirectory, 1800, 4, 7200, 43200 );
    }

    /**
     * Creates valid game settings varying only the preset and the combat modifier; the map is disabled.
     */
    private static GameSettingsForm gameForm( String preset, String combatModifier ) {
        return new GameSettingsForm( preset, combatModifier, "", "", "", "", null, null, null, true );
    }

    private Map< SystemPropertyIf, String > capturedWrite() {
        ArgumentCaptor< SystemPropertyValues > captor = ArgumentCaptor.forClass( SystemPropertyValues.class );
        verify( systemPropertiesService, times( 1 ) ).setValues( eq( LaunchProperty.FILE_NAME ), captor.capture() );
        return captor.getValue().asMap();
    }
}
