package com.github.pplociennik.vdeck.feature.launch.service.impl;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readBoolean;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readInt;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readOptional;
import static java.util.Objects.requireNonNull;

/**
 * Reads and updates the launch profile stored in {@code valheim-server.properties}
 * ({@link LaunchProperty#FILE_NAME}) through {@link SystemPropertiesService}.
 *
 * <p>The profile is edited on two screens, "Serwer" and "Ustawienia gry", and each update writes only the
 * settings of its own screen. An update is all-or-nothing: every check runs before anything is written,
 * and the settings are then stored with a single atomic write of the file. A rejected or failed update ends
 * with a {@link LaunchProfileUpdateException}, which the launch feature's controller advice answers. The
 * save directory is stored normalised, relative to the installation directory, after {@link PathService}
 * has confirmed that it stays inside it.
 *
 * <p>The server password is a secret: it is stored because the server needs it on its command line, but
 * it never leaves this class towards the web layer and is never logged. Log entries name only the
 * outcome, never submitted values.
 */
@Log4j2
@Service
class LaunchProfileServiceImpl implements LaunchProfileService {

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

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

    /**
     * Returns the stored launch profile. Reads the in-memory state of the properties file only, through
     * {@link PropertyReader}: a number or boolean that does not match its setting is replaced by the
     * setting's default, and an unknown preset or modifier value is ignored; both are logged.
     *
     * @return typed launch profile; the password is represented only by whether it is set
     */
    @Override
    public @Nonnull LaunchProfile getProfile() {
        Map< WorldModifier, String > modifiers = new EnumMap<>( WorldModifier.class );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            readOptional( systemPropertiesService, LaunchProperty.FILE_NAME, modifier.getProperty(),
                    value -> Optional.of( value ).filter( modifier::isAllowed ) )
                    .ifPresent( value -> modifiers.put( modifier, value ) );
        }
        Set< WorldKey > enabledKeys = EnumSet.noneOf( WorldKey.class );
        for ( WorldKey key : WorldKey.values() ) {
            LaunchProperty property = key.getProperty();
            if ( readBoolean( systemPropertiesService, LaunchProperty.FILE_NAME, property, property.getDefaultValue() ) ) {
                enabledKeys.add( key );
            }
        }
        WorldPreset preset = readOptional( systemPropertiesService, LaunchProperty.FILE_NAME,
                LaunchProperty.WORLD_PRESET, WorldPreset::fromCliValue ).orElse( null );

        return new LaunchProfile(
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_NAME ),
                readInt( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PORT,
                        LaunchProperty.SERVER_PORT.getDefaultValue() ),
                read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_WORLD ),
                !read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PASSWORD ).isEmpty(),
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
                preset,
                modifiers,
                enabledKeys );
    }

    /**
     * Validates the submitted server settings and stores them with one atomic write of the properties
     * file. Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted server settings that have already passed annotation validation
     * @throws LaunchProfileUpdateException
     *         if a check rejects the settings or the settings file cannot be written; nothing was stored
     */
    @Override
    public void updateServerSettings( @Nonnull ServerSettingsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Server settings update requested" );

        if ( !isComplete( form ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        String saveDirectory = toStoredSaveDirectory( form );
        String password = effectivePassword( form );
        if ( isPartOfName( password, form.serverName() ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.PASSWORD_PART_OF_NAME, form );
        }
        store( "Server settings", toServerValues( form, saveDirectory, password ),
                cause -> LaunchProfileUpdateException.saveFailed( form, cause ) );
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
    public void updateGameSettings( @Nonnull GameSettingsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Game settings update requested" );

        if ( !hasKnownOptions( form ) ) {
            throw LaunchProfileUpdateException.rejected( LaunchProfileMessage.INVALID_INPUT, form );
        }
        store( "Game settings", toGameValues( form ), cause -> LaunchProfileUpdateException.saveFailed( form, cause ) );
    }

    /**
     * Stores the given settings together; the settings of the other screen keep their stored values.
     * A failed write is turned into the process exception built by {@code saveFailure}, which keeps it as
     * its cause; the exception is logged by the controller advice, not here.
     */
    private void store( @Nonnull String scope, @Nonnull SystemPropertyValues values,
                        @Nonnull Function< UncheckedIOException, LaunchProfileUpdateException > saveFailure ) {
        try {
            systemPropertiesService.setValues( LaunchProperty.FILE_NAME, values );
        } catch ( UncheckedIOException exception ) {
            throw saveFailure.apply( exception );
        }
        log.info( "{} stored ({} settings)", scope, values.asMap().size() );
    }

    private static boolean isComplete( @Nonnull ServerSettingsForm form ) {
        return form.serverName() != null && form.port() != null && form.worldName() != null
                && form.saveDirectory() != null && form.saveInterval() != null && form.backupCount() != null
                && form.backupShort() != null && form.backupLong() != null;
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
    private @Nonnull String toStoredSaveDirectory( @Nonnull ServerSettingsForm form ) {
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
    private @Nonnull String effectivePassword( @Nonnull ServerSettingsForm form ) {
        if ( Boolean.TRUE.equals( form.clearPassword() ) ) {
            return "";
        }
        if ( isBlank( form.password() ) ) {
            return read( systemPropertiesService, LaunchProperty.FILE_NAME, LaunchProperty.SERVER_PASSWORD );
        }
        return form.password();
    }

    /**
     * Mirrors the Valheim server's own rule: it refuses to start when the server name contains the
     * password. The comparison ignores letter case, so the panel is never more permissive than the server.
     */
    private static boolean isPartOfName( @Nonnull String password, @Nonnull String serverName ) {
        return !password.isEmpty()
                && serverName.toLowerCase( Locale.ROOT ).contains( password.toLowerCase( Locale.ROOT ) );
    }

    private static @Nonnull SystemPropertyValues toServerValues( @Nonnull ServerSettingsForm form,
                                                                @Nonnull String saveDirectory,
                                                                @Nonnull String password ) {
        return SystemPropertyValues.create()
                .with( LaunchProperty.SERVER_NAME, form.serverName() )
                .with( LaunchProperty.SERVER_PORT, form.port().toString() )
                .with( LaunchProperty.SERVER_WORLD, form.worldName() )
                .with( LaunchProperty.SERVER_PASSWORD, password )
                .with( LaunchProperty.SERVER_PUBLIC, Boolean.toString( Boolean.TRUE.equals( form.publicServer() ) ) )
                .with( LaunchProperty.SERVER_CROSSPLAY, Boolean.toString( Boolean.TRUE.equals( form.crossplay() ) ) )
                .with( LaunchProperty.SERVER_INSTANCE_ID, emptyIfNull( form.instanceId() ) )
                .with( LaunchProperty.SERVER_SAVE_DIRECTORY, saveDirectory )
                .with( LaunchProperty.SERVER_SAVE_INTERVAL, form.saveInterval().toString() )
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