package com.github.pplociennik.vdeck.feature.launch.service.impl;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readBoolean;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readInt;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readOptional;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.LaunchProfileForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerAccessForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerBackupsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerIdentityForm;
import com.github.pplociennik.vdeck.feature.launch.dto.WorldSaveForm;
import com.github.pplociennik.vdeck.feature.launch.exc.LaunchProfileUpdateException;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.shared.PropertyReader;
import com.github.pplociennik.vdeck.shared.SystemPropertyValues;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Reads and updates the launch profile stored in {@code valheim-server.properties}
 * ({@link LaunchProperty#FILE_NAME}) through {@link SystemPropertiesService}.
 *
 * <p>The profile is edited on several panels - "Serwer", "Dostęp", "Świat i zapis", "Kopie automatyczne"
 * and "Ustawienia gry" - and each update writes only the settings of its own panel. An update is
 * all-or-nothing: every check runs before anything is written, and the settings are then stored with a
 * single atomic write of the file. A rejected or failed update ends with a
 * {@link LaunchProfileUpdateException}, which the launch feature's controller advice answers. The save
 * directory is stored normalised, relative to the installation directory, after {@link PathService} has
 * confirmed that it stays inside it. The server name and the password live on different panels, so each
 * of them is checked against the stored value of the other. After a successful write the new
 * configuration is built from the values just stored and the unchanged settings of the other panels, so
 * nothing is read back.
 *
 * <p>The server password is a secret: it is stored because the server needs it on its command line, but
 * only the internal launch configuration carries it. The web layer receives the secret-free profile.
 * Updates and reloads share one monitor; readers capture one immutable configuration without blocking.
 * Log entries name only the outcome, never submitted values.
 */
@Log4j2
@Service
class LaunchProfileServiceImpl implements LaunchProfileService {

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

    /** Current complete configuration; published at startup and after each successful update. */
    private volatile LaunchConfiguration configuration;

    /**
     * Creates the service storing the launch profile.
     *
     * @param systemPropertiesService
     *         service reading and writing {@code valheim-server.properties}
     * @param pathService
     *         service validating the save directory against the installation directory
     */
    LaunchProfileServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                              @Nonnull PathService pathService ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    @Override
    public @Nonnull LaunchProfile getProfile() {
        return getConfiguration().profile();
    }

    @Override
    public @Nonnull LaunchConfiguration getConfiguration() {
        LaunchConfiguration current = configuration;
        if ( current == null ) {
            throw new IllegalStateException( "Launch configuration has not been loaded" );
        }
        return current;
    }

    @Override
    public synchronized void reload() {
        String password = read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PASSWORD );
        publish( new LaunchConfiguration( readProfile( password ), password ) );
    }

    /**
     * Validates the submitted server identity and stores it with one atomic write of the properties file.
     * Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted server identity that has already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    @Override
    public synchronized void updateServerIdentity( @Nonnull ServerIdentityForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Server identity update requested" );

        if ( form.serverName() == null || form.port() == null ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        LaunchConfiguration current = getConfiguration();
        if ( LaunchProfile.isPasswordPartOfName( current.password(), form.serverName() ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        }
        store( "Server identity", toIdentityValues( form ), form );

        publish( new LaunchConfiguration( withServerIdentity( current.profile(), form ), current.password() ) );
    }

    /**
     * Validates the submitted server password and stores it with one atomic write of the properties file.
     * Nothing is stored when the check fails or the file cannot be written.
     *
     * @param form
     *         submitted access settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the password or the settings file cannot be written; nothing was stored
     */
    @Override
    public synchronized void updateServerAccess( @Nonnull ServerAccessForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Server access update requested" );

        LaunchConfiguration current = getConfiguration();
        String password = effectivePassword( form, current.password() );
        if ( LaunchProfile.isPasswordPartOfName( password, current.profile().serverName() ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        }
        store( "Server access", SystemPropertyValues.create().with( LaunchProperty.SERVER_PASSWORD, password ), form );

        publish( new LaunchConfiguration( withPasswordSet( current.profile(), !password.isEmpty() ), password ) );
    }

    /**
     * Validates the submitted world and save settings and stores them with one atomic write of the
     * properties file. Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted world and save settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    @Override
    public synchronized void updateWorldSave( @Nonnull WorldSaveForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "World and save settings update requested" );

        if ( form.worldName() == null || form.saveDirectory() == null || form.saveInterval() == null ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        String saveDirectory = toStoredSaveDirectory( form );
        store( "World and save settings", toWorldSaveValues( form, saveDirectory ), form );

        LaunchConfiguration current = getConfiguration();
        LaunchProfile profile = withWorldSave( current.profile(), form, saveDirectory );
        publish( new LaunchConfiguration( profile, current.password() ) );
    }

    /**
     * Validates the submitted automatic backup settings and stores them with one atomic write of the
     * properties file. Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted backup settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    @Override
    public synchronized void updateServerBackups( @Nonnull ServerBackupsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Automatic backup settings update requested" );

        if ( form.backupCount() == null || form.backupShort() == null || form.backupLong() == null ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        store( "Automatic backup settings", toBackupValues( form ), form );

        LaunchConfiguration current = getConfiguration();
        publish( new LaunchConfiguration( withBackups( current.profile(), form ), current.password() ) );
    }

    /**
     * Validates the submitted game settings and stores them with one atomic write of the properties file.
     * Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted game settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    @Override
    public synchronized void updateGameSettings( @Nonnull GameSettingsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Game settings update requested" );

        if ( !hasKnownOptions( form ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        store( "Game settings", toGameValues( form ), form );

        LaunchConfiguration current = getConfiguration();
        publish( new LaunchConfiguration( withGameSettings( current.profile(), form ), current.password() ) );
    }

    /**
     * Maps loaded values once per publication, retaining PropertyReader's documented fallbacks. Used only at
     * startup: an update builds the new profile from the values it has just stored.
     */
    private @Nonnull LaunchProfile readProfile( @Nonnull String password ) {
        return new LaunchProfile(
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_NAME ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PORT,
                        LaunchProperty.SERVER_PORT.getDefaultValue() ),
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_WORLD ),
                !password.isEmpty(),
                readBoolean( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PUBLIC,
                        LaunchProperty.SERVER_PUBLIC.getDefaultValue() ),
                readBoolean( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_CROSSPLAY,
                        LaunchProperty.SERVER_CROSSPLAY.getDefaultValue() ),
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_INSTANCE_ID ),
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_SAVE_DIRECTORY ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_SAVE_INTERVAL,
                        LaunchProperty.SERVER_SAVE_INTERVAL.getDefaultValue() ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_BACKUP_COUNT,
                        LaunchProperty.SERVER_BACKUP_COUNT.getDefaultValue() ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_BACKUP_SHORT,
                        LaunchProperty.SERVER_BACKUP_SHORT.getDefaultValue() ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_BACKUP_LONG,
                        LaunchProperty.SERVER_BACKUP_LONG.getDefaultValue() ),
                readPreset(),
                readModifiers(),
                readEnabledKeys() );
    }

    /** Reads the world preset; an unknown stored value is skipped as if no preset were applied. */
    private @Nullable WorldPreset readPreset() {
        return readOptional( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.WORLD_PRESET,
                WorldPreset::fromCliValue ).orElse( null );
    }

    /** Reads the modifiers that are set; an empty or unknown stored value leaves the modifier out. */
    private @Nonnull Map< WorldModifier, String > readModifiers() {
        Map< WorldModifier, String > modifiers = new EnumMap<>( WorldModifier.class );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            readOptional( systemPropertiesService, LaunchProperty.FILE_NAME, modifier.getProperty(),
                    value -> Optional.of( value ).filter( modifier::isAllowed ) )
                    .ifPresent( value -> modifiers.put( modifier, value ) );
        }
        return modifiers;
    }

    /** Reads the enabled world keys; a stored value that is not a boolean falls back to the key's default. */
    private @Nonnull Set< WorldKey > readEnabledKeys() {
        Set< WorldKey > enabledKeys = EnumSet.noneOf( WorldKey.class );
        for ( WorldKey key : WorldKey.values() ) {
            LaunchProperty property = key.getProperty();
            if ( readBoolean( systemPropertiesService, LaunchProperty.FILE_NAME, property, property.getDefaultValue() ) ) {
                enabledKeys.add( key );
            }
        }
        return enabledKeys;
    }

    /**
     * Stores the given settings together; the settings of the other panels keep their stored values.
     * A failed write is turned into the process exception carrying the submitted {@code form} and the
     * failure as its cause; the exception is logged by the controller advice, not here.
     */
    private void store( @Nonnull String scope, @Nonnull SystemPropertyValues values,
                        @Nonnull LaunchProfileForm form ) {
        try {
            systemPropertiesService.setValues( LaunchProperty.FILE_NAME, values );
        } catch ( UncheckedIOException exception ) {
            throw LaunchProfileUpdateException.saveFailed( form, exception );
        }
        log.info( "{} stored ({} settings)", scope, values.asMap().size() );
    }

    private void publish( @Nonnull LaunchConfiguration published ) {
        configuration = published;
        log.debug( "Published the current launch configuration" );
    }

    /**
     * Returns the profile with the "Serwer" panel's settings replaced by the values just stored; every
     * other setting is kept from {@code current}, because the other panels did not change it.
     */
    private static @Nonnull LaunchProfile withServerIdentity( @Nonnull LaunchProfile current,
                                                             @Nonnull ServerIdentityForm form ) {
        return new LaunchProfile(
                form.serverName(),
                form.port(),
                current.worldName(),
                current.passwordSet(),
                Boolean.TRUE.equals( form.publicServer() ),
                Boolean.TRUE.equals( form.crossplay() ),
                emptyIfNull( form.instanceId() ),
                current.saveDirectory(),
                current.saveInterval(),
                current.backupCount(),
                current.backupShort(),
                current.backupLong(),
                current.preset(),
                current.modifiers(),
                current.enabledKeys() );
    }

    /**
     * Returns the profile with the "Dostęp" panel's outcome - whether a password is set - replaced; every
     * other setting is kept from {@code current}.
     */
    private static @Nonnull LaunchProfile withPasswordSet( @Nonnull LaunchProfile current, boolean passwordSet ) {
        return new LaunchProfile(
                current.serverName(),
                current.port(),
                current.worldName(),
                passwordSet,
                current.publicServer(),
                current.crossplay(),
                current.instanceId(),
                current.saveDirectory(),
                current.saveInterval(),
                current.backupCount(),
                current.backupShort(),
                current.backupLong(),
                current.preset(),
                current.modifiers(),
                current.enabledKeys() );
    }

    /**
     * Returns the profile with the "Świat i zapis" panel's settings replaced by the values just stored;
     * every other setting is kept from {@code current}.
     */
    private static @Nonnull LaunchProfile withWorldSave( @Nonnull LaunchProfile current, @Nonnull WorldSaveForm form,
                                                        @Nonnull String saveDirectory ) {
        return new LaunchProfile(
                current.serverName(),
                current.port(),
                form.worldName(),
                current.passwordSet(),
                current.publicServer(),
                current.crossplay(),
                current.instanceId(),
                saveDirectory,
                form.saveInterval(),
                current.backupCount(),
                current.backupShort(),
                current.backupLong(),
                current.preset(),
                current.modifiers(),
                current.enabledKeys() );
    }

    /**
     * Returns the profile with the "Kopie automatyczne" panel's settings replaced by the values just
     * stored; every other setting is kept from {@code current}.
     */
    private static @Nonnull LaunchProfile withBackups( @Nonnull LaunchProfile current,
                                                      @Nonnull ServerBackupsForm form ) {
        return new LaunchProfile(
                current.serverName(),
                current.port(),
                current.worldName(),
                current.passwordSet(),
                current.publicServer(),
                current.crossplay(),
                current.instanceId(),
                current.saveDirectory(),
                current.saveInterval(),
                form.backupCount(),
                form.backupShort(),
                form.backupLong(),
                current.preset(),
                current.modifiers(),
                current.enabledKeys() );
    }

    /**
     * Returns the profile with the "Ustawienia gry" panel's settings replaced by the values just stored;
     * every other setting is kept from {@code current}.
     */
    private static @Nonnull LaunchProfile withGameSettings( @Nonnull LaunchProfile current,
                                                           @Nonnull GameSettingsForm form ) {
        return new LaunchProfile(
                current.serverName(),
                current.port(),
                current.worldName(),
                current.passwordSet(),
                current.publicServer(),
                current.crossplay(),
                current.instanceId(),
                current.saveDirectory(),
                current.saveInterval(),
                current.backupCount(),
                current.backupShort(),
                current.backupLong(),
                form.presetValue(),
                toModifiers( form ),
                toEnabledKeys( form ) );
    }

    private static @Nonnull Map< WorldModifier, String > toModifiers( @Nonnull GameSettingsForm form ) {
        Map< WorldModifier, String > modifiers = new EnumMap<>( WorldModifier.class );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            String value = form.modifierValue( modifier );
            if ( !isBlank( value ) ) {
                modifiers.put( modifier, value );
            }
        }
        return modifiers;
    }

    private static @Nonnull Set< WorldKey > toEnabledKeys( @Nonnull GameSettingsForm form ) {
        Set< WorldKey > enabledKeys = EnumSet.noneOf( WorldKey.class );
        for ( WorldKey key : WorldKey.values() ) {
            if ( form.isKeyEnabled( key ) ) {
                enabledKeys.add( key );
            }
        }
        return enabledKeys;
    }

    /**
     * Checks the values the annotations cannot: the preset and every modifier must be empty or one of
     * the options the server accepts.
     */
    private static boolean hasKnownOptions( @Nonnull GameSettingsForm form ) {
        if ( !isBlank( form.preset() ) && form.presetValue() == null ) {
            return false;
        }
        for ( WorldModifier modifier : WorldModifier.values() ) {
            String value = form.modifierValue( modifier );
            if ( !isBlank( value ) && !modifier.isAllowed( value ) ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the submitted save directory through {@link PathService} and returns it relative to the
     * installation directory, with forward slashes. The installation directory itself is refused, because
     * worlds would then be mixed with the server and panel files. A refused directory is a rejection of
     * the submitted value, so the reason is logged at {@code DEBUG} and the exception carries no cause.
     */
    private @Nonnull String toStoredSaveDirectory( @Nonnull WorldSaveForm form ) {
        Path resolved;
        try {
            resolved = pathService.getAbsolutePath( form.saveDirectory() );
        } catch ( PathResolutionException | IllegalArgumentException | UncheckedIOException exception ) {
            log.debug( "Save directory rejected: {}", exception.getMessage() );
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_SAVE_DIRECTORY, form );
        }
        Path baseDirectory = pathService.getBaseDirectory();
        if ( resolved.equals( baseDirectory ) ) {
            log.debug( "Save directory rejected: it is the installation directory itself" );
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_SAVE_DIRECTORY, form );
        }
        return baseDirectory.relativize( resolved ).toString().replace( '\\', '/' );
    }

    /**
     * Returns the password to store: none when removal was requested, the stored one when the field was
     * left empty, and the submitted one otherwise.
     */
    private static @Nonnull String effectivePassword( @Nonnull ServerAccessForm form,
                                                      @Nonnull String storedPassword ) {
        if ( Boolean.TRUE.equals( form.clearPassword() ) ) {
            return "";
        }
        if ( isBlank( form.password() ) ) {
            return storedPassword;
        }
        return form.password();
    }

    private static @Nonnull SystemPropertyValues toIdentityValues( @Nonnull ServerIdentityForm form ) {
        return SystemPropertyValues.create()
                .with( LaunchProperty.SERVER_NAME, form.serverName() )
                .with( LaunchProperty.SERVER_PORT, form.port().toString() )
                .with( LaunchProperty.SERVER_PUBLIC, Boolean.toString( Boolean.TRUE.equals( form.publicServer() ) ) )
                .with( LaunchProperty.SERVER_CROSSPLAY, Boolean.toString( Boolean.TRUE.equals( form.crossplay() ) ) )
                .with( LaunchProperty.SERVER_INSTANCE_ID, emptyIfNull( form.instanceId() ) );
    }

    private static @Nonnull SystemPropertyValues toWorldSaveValues( @Nonnull WorldSaveForm form,
                                                                   @Nonnull String saveDirectory ) {
        return SystemPropertyValues.create()
                .with( LaunchProperty.SERVER_WORLD, form.worldName() )
                .with( LaunchProperty.SERVER_SAVE_DIRECTORY, saveDirectory )
                .with( LaunchProperty.SERVER_SAVE_INTERVAL, form.saveInterval().toString() );
    }

    private static @Nonnull SystemPropertyValues toBackupValues( @Nonnull ServerBackupsForm form ) {
        return SystemPropertyValues.create()
                .with( LaunchProperty.SERVER_BACKUP_COUNT, form.backupCount().toString() )
                .with( LaunchProperty.SERVER_BACKUP_SHORT, form.backupShort().toString() )
                .with( LaunchProperty.SERVER_BACKUP_LONG, form.backupLong().toString() );
    }

    private static @Nonnull SystemPropertyValues toGameValues( @Nonnull GameSettingsForm form ) {
        WorldPreset preset = form.presetValue();
        SystemPropertyValues values = SystemPropertyValues.create()
                .with( LaunchProperty.WORLD_PRESET, preset == null ? "" : preset.getCliValue() );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            values.with( modifier.getProperty(), emptyIfNull( form.modifierValue( modifier ) ) );
        }
        for ( WorldKey key : WorldKey.values() ) {
            values.with( key.getProperty(), Boolean.toString( form.isKeyEnabled( key ) ) );
        }
        return values;
    }

    private static boolean isBlank( @Nullable String value ) {
        return value == null || value.isBlank();
    }

    private static @Nonnull String emptyIfNull( @Nullable String value ) {
        return value == null ? "" : value;
    }
}
