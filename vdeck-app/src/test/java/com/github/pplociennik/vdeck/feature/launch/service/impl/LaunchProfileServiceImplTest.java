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
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerBackupsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerIdentityForm;
import com.github.pplociennik.vdeck.feature.launch.dto.WorldSaveForm;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that {@link LaunchProfileServiceImpl} maps the stored launch profile to its typed view,
 * validates the settings submitted from every panel and stores an accepted panel with a single write of
 * only its own settings, without the server password ever entering the web profile.
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
     * installation directory that accepts every relative save directory. The configuration is published
     * as at startup, because an update builds the new configuration on the current one.
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
        underTest.reload();
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
        var notLoaded = new LaunchProfileServiceImpl( systemPropertiesService, pathService );

        // WHEN
        var thrown = catchThrowable( notLoaded::getConfiguration );

        // THEN
        assertThat( thrown ).isInstanceOf( IllegalStateException.class );
    }

    /**
     * Verifies that a server identity edit publishes the stored identity together with the unchanged
     * password and game settings, built from the submitted values without reading the settings back.
     */
    @Test
    void shouldPublishConfiguration_whenServerIdentityIsSaved() {
        // GIVEN
        storedValues.put( LaunchProperty.WORLD_PRESET, "hard" );
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "UnchangedSecret" );
        underTest.reload();
        var previous = underTest.getConfiguration();
        clearInvocations( systemPropertiesService );

        // WHEN
        underTest.updateServerIdentity( identityForm( "Changed server" ) );

        // THEN
        var result = underTest.getConfiguration();
        assertThat( result ).isNotSameAs( previous );
        assertThat( result.profile().serverName() ).isEqualTo( "Changed server" );
        assertThat( result.profile().port() ).isEqualTo( 2457 );
        assertThat( result.profile().publicServer() ).isFalse();
        assertThat( result.profile().crossplay() ).isTrue();
        assertThat( result.password() ).isEqualTo( "UnchangedSecret" );
        assertThat( result.profile().passwordSet() ).isTrue();
        assertThat( result.profile().preset() ).isEqualTo( WorldPreset.HARD );
        assertThat( previous.profile().serverName() ).isEqualTo( "vDeck" );
        verify( systemPropertiesService, never() ).getValue( any(), any() );
    }

    /**
     * Verifies that a world and save edit publishes the stored world settings and keeps the rest of the
     * profile.
     */
    @Test
    void shouldPublishConfiguration_whenWorldSaveIsSaved() {
        // GIVEN
        clearInvocations( systemPropertiesService );

        // WHEN
        underTest.updateWorldSave( new WorldSaveForm( "Midgard", "worlds", 600 ) );

        // THEN
        var result = underTest.getProfile();
        assertThat( result.worldName() ).isEqualTo( "Midgard" );
        assertThat( result.saveDirectory() ).isEqualTo( "worlds" );
        assertThat( result.saveInterval() ).isEqualTo( 600 );
        assertThat( result.serverName() ).isEqualTo( "vDeck" );
        assertThat( result.backupCount() ).isEqualTo( 4 );
        verify( systemPropertiesService, never() ).getValue( any(), any() );
    }

    /**
     * Verifies that a backup edit publishes the stored backup settings and keeps the rest of the profile.
     */
    @Test
    void shouldPublishConfiguration_whenServerBackupsAreSaved() {
        // GIVEN
        clearInvocations( systemPropertiesService );

        // WHEN
        underTest.updateServerBackups( new ServerBackupsForm( 10, 3600, 86400 ) );

        // THEN
        var result = underTest.getProfile();
        assertThat( result.backupCount() ).isEqualTo( 10 );
        assertThat( result.backupShort() ).isEqualTo( 3600 );
        assertThat( result.backupLong() ).isEqualTo( 86400 );
        assertThat( result.worldName() ).isEqualTo( "Dedicated" );
        verify( systemPropertiesService, never() ).getValue( any(), any() );
    }

    /**
     * Verifies that game edits publish the stored world settings and keep the server settings and
     * credentials, built from the submitted values without reading the settings back.
     */
    @Test
    void shouldRefreshGameSettings_whenGameFormIsSaved() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "UnchangedSecret" );
        storedValues.put( LaunchProperty.SERVER_NAME, "Unchanged server" );
        underTest.reload();
        clearInvocations( systemPropertiesService );

        // WHEN
        underTest.updateGameSettings( gameForm( "hard", "veryhard" ) );

        // THEN
        var result = underTest.getConfiguration();
        assertThat( result.profile().preset() ).isEqualTo( WorldPreset.HARD );
        assertThat( result.profile().modifiers() ).containsOnly( Map.entry( WorldModifier.COMBAT, "veryhard" ) );
        assertThat( result.profile().enabledKeys() ).containsExactly( WorldKey.NO_MAP );
        assertThat( result.profile().serverName() ).isEqualTo( "Unchanged server" );
        assertThat( result.password() ).isEqualTo( "UnchangedSecret" );
        verify( systemPropertiesService, never() ).getValue( any(), any() );
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
            var update = executor.submit( () -> underTest.updateServerAccess( accessForm( "NewSecret", false ) ) );
            try {
                assertThat( writing.await( 5, TimeUnit.SECONDS ) ).isTrue();
                var duringWrite = underTest.getConfiguration();

                // THEN
                assertThat( duringWrite ).isSameAs( previous );
            } finally {
                finishWrite.countDown();
            }
            update.get( 5, TimeUnit.SECONDS );
            assertThat( underTest.getConfiguration().password() ).isEqualTo( "NewSecret" );
            assertThat( underTest.getProfile().passwordSet() ).isTrue();
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
     * Verifies that a valid server identity is stored with a single write carrying only the identity
     * settings, so every other setting keeps its stored value.
     */
    @Test
    void shouldStoreOnlyIdentitySettingsInOneWrite_whenIdentityFormIsValid() {
        // GIVEN
        ServerIdentityForm form = identityForm( "My server" );

        // WHEN
        underTest.updateServerIdentity( form );

        // THEN
        Map< SystemPropertyIf, String > written = capturedWrite();
        assertThat( written ).containsOnlyKeys( LaunchProperty.SERVER_NAME, LaunchProperty.SERVER_PORT,
                LaunchProperty.SERVER_PUBLIC, LaunchProperty.SERVER_CROSSPLAY, LaunchProperty.SERVER_INSTANCE_ID );
        assertThat( written )
                .containsEntry( LaunchProperty.SERVER_NAME, "My server" )
                .containsEntry( LaunchProperty.SERVER_PORT, "2457" )
                .containsEntry( LaunchProperty.SERVER_PUBLIC, "false" )
                .containsEntry( LaunchProperty.SERVER_CROSSPLAY, "true" )
                .containsEntry( LaunchProperty.SERVER_INSTANCE_ID, "" );
        verify( pathService, never() ).getAbsolutePath( any() );
    }

    /**
     * Verifies that an unchecked checkbox and a missing instance identifier, both submitted as nothing,
     * are stored as {@code false} and empty.
     */
    @Test
    void shouldStoreFalseAndEmpty_whenOptionalIdentityFieldsAreMissing() {
        // GIVEN
        ServerIdentityForm form = new ServerIdentityForm( "My server", 2456, null, null, null );

        // WHEN
        underTest.updateServerIdentity( form );

        // THEN
        assertThat( capturedWrite() )
                .containsEntry( LaunchProperty.SERVER_PUBLIC, "false" )
                .containsEntry( LaunchProperty.SERVER_CROSSPLAY, "false" )
                .containsEntry( LaunchProperty.SERVER_INSTANCE_ID, "" );
    }

    /**
     * Verifies that a new password is stored with a single write carrying only the password.
     */
    @Test
    void shouldStoreOnlyPasswordInOneWrite_whenAccessFormIsValid() {
        // GIVEN
        ServerAccessForm form = accessForm( "Secret1", false );

        // WHEN
        underTest.updateServerAccess( form );

        // THEN
        assertThat( capturedWrite() ).containsOnly( Map.entry( LaunchProperty.SERVER_PASSWORD, "Secret1" ) );
    }

    /**
     * Verifies that valid world and save settings are stored with a single write carrying only those
     * settings.
     */
    @Test
    void shouldStoreOnlyWorldSettingsInOneWrite_whenWorldFormIsValid() {
        // GIVEN
        WorldSaveForm form = worldForm( "worlds/main" );

        // WHEN
        underTest.updateWorldSave( form );

        // THEN
        assertThat( capturedWrite() ).containsOnly(
                Map.entry( LaunchProperty.SERVER_WORLD, "Dedicated" ),
                Map.entry( LaunchProperty.SERVER_SAVE_DIRECTORY, "worlds/main" ),
                Map.entry( LaunchProperty.SERVER_SAVE_INTERVAL, "1800" ) );
    }

    /**
     * Verifies that valid backup settings are stored with a single write carrying only those settings.
     */
    @Test
    void shouldStoreOnlyBackupSettingsInOneWrite_whenBackupsFormIsValid() {
        // GIVEN
        ServerBackupsForm form = new ServerBackupsForm( 1, 60, 2_592_000 );

        // WHEN
        underTest.updateServerBackups( form );

        // THEN
        assertThat( capturedWrite() ).containsOnly(
                Map.entry( LaunchProperty.SERVER_BACKUP_COUNT, "1" ),
                Map.entry( LaunchProperty.SERVER_BACKUP_SHORT, "60" ),
                Map.entry( LaunchProperty.SERVER_BACKUP_LONG, "2592000" ) );
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
        WorldSaveForm form = worldForm( "./worlds/../worlds/main" );

        // WHEN
        underTest.updateWorldSave( form );

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
        underTest.reload();
        ServerAccessForm form = accessForm( "", false );

        // WHEN
        underTest.updateServerAccess( form );

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
        underTest.reload();
        ServerAccessForm form = accessForm( "Typed12", true );

        // WHEN
        underTest.updateServerAccess( form );

        // THEN
        assertThat( capturedWrite() ).containsEntry( LaunchProperty.SERVER_PASSWORD, "" );
    }

    /**
     * Verifies that a new password contained in the stored server name is refused regardless of letter
     * case, because the Valheim server would refuse to start with it.
     */
    @Test
    void shouldRejectPassword_whenItIsPartOfStoredServerName() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_NAME, "Vikings of the North" );
        underTest.reload();
        ServerAccessForm form = accessForm( "viking", false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerAccess( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME );
            assertThat( exception.getSubmittedForm() ).isEqualTo( accessForm( null, false ) );
        } );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that the stored password is checked against a changed server name as well.
     */
    @Test
    void shouldRejectServerName_whenItContainsStoredPassword() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "north" );
        underTest.reload();
        ServerIdentityForm form = identityForm( "Vikings of the North" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerIdentity( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() )
                        .isEqualTo( LaunchProfileMessage.PASSWORD_PART_OF_NAME ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that removing the password is accepted even when the stored password is part of the
     * server name, because no password remains to conflict with it.
     */
    @Test
    void shouldAcceptRemoval_whenStoredPasswordIsPartOfServerName() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_NAME, "Vikings of the North" );
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "north" );
        underTest.reload();

        // WHEN
        underTest.updateServerAccess( accessForm( "", true ) );

        // THEN
        assertThat( capturedWrite() ).containsEntry( LaunchProperty.SERVER_PASSWORD, "" );
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
     * Verifies that an identity form missing a required value is rejected, which protects callers that
     * skip the annotation validation.
     */
    @Test
    void shouldRejectIdentityUpdate_whenRequiredValueIsMissing() {
        // GIVEN
        ServerIdentityForm form = new ServerIdentityForm( "My server", null, true, false, "" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerIdentity( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT ) );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a world form missing a required value is rejected before the save directory is
     * resolved.
     */
    @Test
    void shouldRejectWorldUpdate_whenRequiredValueIsMissing() {
        // GIVEN
        WorldSaveForm form = new WorldSaveForm( "Dedicated", null, 1800 );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateWorldSave( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class,
                exception -> assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.INVALID_INPUT ) );
        verify( pathService, never() ).getAbsolutePath( any() );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a backups form missing a required value is rejected and nothing is stored.
     */
    @Test
    void shouldRejectBackupsUpdate_whenRequiredValueIsMissing() {
        // GIVEN
        ServerBackupsForm form = new ServerBackupsForm( 4, 7200, null );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerBackups( form ) );

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
        WorldSaveForm form = worldForm( "../outside" );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateWorldSave( form ) );

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
        WorldSaveForm form = worldForm( "." );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateWorldSave( form ) );

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
        ServerAccessForm form = accessForm( "Secret1", false );

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerAccess( form ) );

        // THEN
        assertThat( thrown ).isInstanceOfSatisfying( LaunchProfileUpdateException.class, exception -> {
            assertThat( exception.getMessageKey() ).isEqualTo( LaunchProfileMessage.SAVE_FAILED );
            assertThat( exception.getCause() ).isSameAs( failure );
            assertThat( exception.getSubmittedForm() ).isEqualTo( form.withoutSecrets() );
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
     * Verifies that a null identity form is rejected before anything is written.
     */
    @Test
    void shouldThrowException_whenIdentityFormIsNull() {
        // GIVEN
        // no form at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerIdentity( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "form must not be null" );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a null access form is rejected before anything is written.
     */
    @Test
    void shouldThrowException_whenAccessFormIsNull() {
        // GIVEN
        // no form at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerAccess( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "form must not be null" );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a null world form is rejected before anything is resolved or written.
     */
    @Test
    void shouldThrowException_whenWorldFormIsNull() {
        // GIVEN
        // no form at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateWorldSave( null ) );

        // THEN
        assertThat( thrown )
                .isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "form must not be null" );
        verify( pathService, never() ).getAbsolutePath( any() );
        verify( systemPropertiesService, never() ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
    }

    /**
     * Verifies that a null backups form is rejected before anything is written.
     */
    @Test
    void shouldThrowException_whenBackupsFormIsNull() {
        // GIVEN
        // no form at all

        // WHEN
        Throwable thrown = catchThrowable( () -> underTest.updateServerBackups( null ) );

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

    /** Verifies removal is reflected in both the captured secret and the web profile flag. */
    @Test
    void shouldPublishPasswordRemoval_whenRemovalIsSaved() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "SyntheticSecret" );
        underTest.reload();
        var previous = underTest.getConfiguration();

        // WHEN
        underTest.updateServerAccess( accessForm( "", true ) );

        // THEN
        assertThat( underTest.getConfiguration().password() ).isEmpty();
        assertThat( underTest.getProfile().passwordSet() ).isFalse();
        assertThat( previous.password() ).isEqualTo( "SyntheticSecret" );
        assertThat( previous.profile().passwordSet() ).isTrue();
    }

    /** Verifies leaving the password field blank preserves the secret in the newly published version. */
    @Test
    void shouldPublishKeptPassword_whenPasswordFieldIsBlank() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "SyntheticSecret" );
        underTest.reload();
        var previous = underTest.getConfiguration();

        // WHEN
        underTest.updateServerAccess( accessForm( "  ", false ) );

        // THEN
        assertThat( underTest.getConfiguration() ).isNotSameAs( previous );
        assertThat( underTest.getConfiguration().password() ).isEqualTo( "SyntheticSecret" );
        assertThat( underTest.getProfile().passwordSet() ).isTrue();
    }

    /** Verifies a failed password write cannot publish the submitted replacement password. */
    @Test
    void shouldKeepSecretAndConfiguration_whenServerWriteFails() {
        // GIVEN
        storedValues.put( LaunchProperty.SERVER_PASSWORD, "OriginalSyntheticSecret" );
        underTest.reload();
        var previous = underTest.getConfiguration();
        var failure = new UncheckedIOException( new IOException( "Synthetic write failure" ) );
        willThrow( failure ).given( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );

        // WHEN
        var thrown = catchThrowable( () -> underTest.updateServerAccess(
                accessForm( "ReplacementSyntheticSecret", false ) ) );

        // THEN
        assertThat( thrown ).isInstanceOf( LaunchProfileUpdateException.class ).hasCause( failure );
        assertThat( underTest.getConfiguration() ).isSameAs( previous );
        assertThat( previous.password() ).isEqualTo( "OriginalSyntheticSecret" );
    }

    /**
     * Verifies overlapping writes from two settings panels publish both changes together.
     *
     * @throws Exception
     *         if coordinated writers fail or exceed their bounded waits
     */
    @Test
    void shouldPreserveBothPanels_whenServerAndGameUpdatesOverlap() throws Exception {
        // GIVEN
        storedValues = Collections.synchronizedMap( storedValues );
        underTest.reload();
        var writingServer = new CountDownLatch( 1 );
        var releaseServer = new CountDownLatch( 1 );
        var gameRequested = new CountDownLatch( 1 );
        doAnswer( invocation -> {
            SystemPropertyValues values = invocation.getArgument( 1 );
            if ( values.asMap().containsKey( LaunchProperty.SERVER_NAME ) ) {
                writingServer.countDown();
                if ( !releaseServer.await( 5, TimeUnit.SECONDS ) ) {
                    throw new IllegalStateException( "Server write was not released" );
                }
            }
            applyStoredValues( values );
            return null;
        } ).when( systemPropertiesService ).setValues( eq( LaunchProperty.FILE_NAME ), any() );
        try ( var executor = Executors.newFixedThreadPool( 2 ) ) {
            var serverUpdate = executor.submit( () -> underTest.updateServerIdentity( identityForm( "New server" ) ) );
            try {
                assertThat( writingServer.await( 5, TimeUnit.SECONDS ) ).isTrue();
                var gameUpdate = executor.submit( () -> {
                    gameRequested.countDown();
                    underTest.updateGameSettings( gameForm( "hard", "veryhard" ) );
                } );

                // WHEN
                assertThat( gameRequested.await( 5, TimeUnit.SECONDS ) ).isTrue();
                releaseServer.countDown();
                serverUpdate.get( 5, TimeUnit.SECONDS );
                gameUpdate.get( 5, TimeUnit.SECONDS );

                // THEN
                var result = underTest.getConfiguration();
                assertThat( result.profile().serverName() ).isEqualTo( "New server" );
                assertThat( result.profile().preset() ).isEqualTo( WorldPreset.HARD );
                assertThat( result.profile().modifiers() ).containsEntry( WorldModifier.COMBAT, "veryhard" );
                assertThat( storedValues ).containsEntry( LaunchProperty.SERVER_NAME, "New server" )
                        .containsEntry( LaunchProperty.WORLD_PRESET, "hard" );
            } finally {
                releaseServer.countDown();
            }
        }
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void applyStoredValues( SystemPropertyValues values ) {
        values.asMap().forEach( ( property, value ) -> storedValues.put( ( LaunchProperty ) property, value.strip() ) );
    }

    /**
     * Creates a valid server identity varying only the name; the server is private, runs on crossplay and
     * uses port 2457.
     */
    private static ServerIdentityForm identityForm( String serverName ) {
        return new ServerIdentityForm( serverName, 2457, false, true, "" );
    }

    private static ServerAccessForm accessForm( String password, boolean clearPassword ) {
        return new ServerAccessForm( password, clearPassword );
    }

    /**
     * Creates valid world and save settings varying only the save directory.
     */
    private static WorldSaveForm worldForm( String saveDirectory ) {
        return new WorldSaveForm( "Dedicated", saveDirectory, 1800 );
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
