package com.github.pplociennik.vdeck.feature.launch.service.impl;

import com.github.pplociennik.vdeck.feature.launch.LaunchProfileMessage;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfileUpdateResult;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProperty;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.launch.dto.GameSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.dto.ServerSettingsForm;
import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.shared.SystemPropertyIf;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Reads and updates the launch profile through the service owning {@code valheim-server.properties}.
 *
 * <p>The profile is edited on two screens, "Serwer" and "Ustawienia gry", and each update writes only the
 * settings of its own screen. An update is all-or-nothing: every check runs before anything is written,
 * and the settings are then stored with a single atomic write of the file. The save directory is stored normalised, relative
 * to the installation directory, after {@link PathService} has confirmed that it stays inside it.
 *
 * <p>The server password is a secret: it is stored because the server needs it on its command line, but
 * it never leaves this class towards the web layer and is never logged. Log entries name only the
 * outcome, never submitted values.
 */
@Log4j2
@Service
class LaunchProfileServiceImpl implements LaunchProfileService {

    private final SystemPropertiesService launchProperties;

    private final PathService pathService;

    /**
     * Creates the service storing the launch profile.
     *
     * @param launchProperties
     *         service owning {@code valheim-server.properties}
     * @param pathService
     *         service validating the save directory against the installation directory
     */
    LaunchProfileServiceImpl( @Qualifier( LaunchProperty.SERVICE_QUALIFIER ) @Nonnull SystemPropertiesService launchProperties,
                              @Nonnull PathService pathService ) {
        this.launchProperties = requireNonNull( launchProperties, "launchProperties must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    /**
     * Returns the stored launch profile. Reads the in-memory state of the properties file only.
     * A stored value that does not match its setting is replaced by the setting's default and logged.
     *
     * @return typed launch profile; the password is represented only by whether it is set
     */
    @Override
    public @Nonnull LaunchProfile getProfile() {
        Map< WorldModifier, String > modifiers = new EnumMap<>( WorldModifier.class );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            readModifier( modifier ).ifPresent( value -> modifiers.put( modifier, value ) );
        }
        Set< WorldKey > enabledKeys = EnumSet.noneOf( WorldKey.class );
        for ( WorldKey key : WorldKey.values() ) {
            if ( readBoolean( key.getProperty() ) ) {
                enabledKeys.add( key );
            }
        }

        return new LaunchProfile(
                read( LaunchProperty.SERVER_NAME ),
                readInt( LaunchProperty.SERVER_PORT ),
                read( LaunchProperty.SERVER_WORLD ),
                !read( LaunchProperty.SERVER_PASSWORD ).isEmpty(),
                readBoolean( LaunchProperty.SERVER_PUBLIC ),
                readBoolean( LaunchProperty.SERVER_CROSSPLAY ),
                read( LaunchProperty.SERVER_INSTANCE_ID ),
                read( LaunchProperty.SERVER_SAVE_DIRECTORY ),
                readInt( LaunchProperty.SERVER_SAVE_INTERVAL ),
                readInt( LaunchProperty.SERVER_BACKUP_COUNT ),
                readInt( LaunchProperty.SERVER_BACKUP_SHORT ),
                readInt( LaunchProperty.SERVER_BACKUP_LONG ),
                readPreset(),
                modifiers,
                enabledKeys );
    }

    /**
     * Validates the submitted server settings and stores them with one atomic write of the properties
     * file. Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted server settings that have already passed annotation validation
     * @return successful result, or a rejection carrying the message to show
     */
    @Override
    public @Nonnull LaunchProfileUpdateResult updateServerSettings( @Nonnull ServerSettingsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Server settings update requested" );

        if ( !isComplete( form ) ) {
            return reject( LaunchProfileMessage.INVALID_INPUT );
        }
        Optional< String > saveDirectory = toStoredSaveDirectory( form.saveDirectory() );
        if ( saveDirectory.isEmpty() ) {
            return reject( LaunchProfileMessage.INVALID_SAVE_DIRECTORY );
        }
        String password = effectivePassword( form );
        if ( isPartOfName( password, form.serverName() ) ) {
            return reject( LaunchProfileMessage.PASSWORD_PART_OF_NAME );
        }
        return store( "Server settings", toServerValues( form, saveDirectory.get(), password ) );
    }

    /**
     * Validates the submitted game settings and stores them with one atomic write of the properties file.
     * Nothing is stored when any check fails or the file cannot be written.
     *
     * @param form
     *         submitted game settings that have already passed annotation validation
     * @return successful result, or a rejection carrying the message to show
     */
    @Override
    public @Nonnull LaunchProfileUpdateResult updateGameSettings( @Nonnull GameSettingsForm form ) {
        requireNonNull( form, "form must not be null" );
        log.info( "Game settings update requested" );

        if ( !hasKnownOptions( form ) ) {
            return reject( LaunchProfileMessage.INVALID_INPUT );
        }
        return store( "Game settings", toGameValues( form ) );
    }

    /**
     * Stores the given settings together; the settings of the other screen keep their stored values.
     */
    private @Nonnull LaunchProfileUpdateResult store( @Nonnull String scope, @Nonnull Map< SystemPropertyIf, String > values ) {
        try {
            launchProperties.setValues( values );
        } catch ( UncheckedIOException exception ) {
            log.error( "Failed to store the launch profile", exception );
            return LaunchProfileUpdateResult.rejected( LaunchProfileMessage.SAVE_FAILED );
        }
        log.info( "{} stored ({} settings)", scope, values.size() );
        return LaunchProfileUpdateResult.success();
    }

    private @Nonnull LaunchProfileUpdateResult reject( @Nonnull LaunchProfileMessage message ) {
        log.info( "Launch profile update rejected: {}", message );
        return LaunchProfileUpdateResult.rejected( message );
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
     * worlds would then be mixed with the server and panel files.
     */
    private @Nonnull Optional< String > toStoredSaveDirectory( @Nonnull String submittedDirectory ) {
        Path resolved;
        try {
            resolved = pathService.getAbsolutePath( submittedDirectory );
        } catch ( PathResolutionException | IllegalArgumentException | UncheckedIOException exception ) {
            log.debug( "Save directory rejected: {}", exception.getMessage() );
            return Optional.empty();
        }
        Path baseDirectory = pathService.getBaseDirectory();
        if ( resolved.equals( baseDirectory ) ) {
            log.debug( "Save directory rejected: it is the installation directory itself" );
            return Optional.empty();
        }
        return Optional.of( baseDirectory.relativize( resolved ).toString().replace( '\\', '/' ) );
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
            return read( LaunchProperty.SERVER_PASSWORD );
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

    private static @Nonnull Map< SystemPropertyIf, String > toServerValues( @Nonnull ServerSettingsForm form,
                                                                           @Nonnull String saveDirectory,
                                                                           @Nonnull String password ) {
        Map< SystemPropertyIf, String > values = new LinkedHashMap<>();
        values.put( LaunchProperty.SERVER_NAME, form.serverName() );
        values.put( LaunchProperty.SERVER_PORT, form.port().toString() );
        values.put( LaunchProperty.SERVER_WORLD, form.worldName() );
        values.put( LaunchProperty.SERVER_PASSWORD, password );
        values.put( LaunchProperty.SERVER_PUBLIC, Boolean.toString( Boolean.TRUE.equals( form.publicServer() ) ) );
        values.put( LaunchProperty.SERVER_CROSSPLAY, Boolean.toString( Boolean.TRUE.equals( form.crossplay() ) ) );
        values.put( LaunchProperty.SERVER_INSTANCE_ID, emptyIfNull( form.instanceId() ) );
        values.put( LaunchProperty.SERVER_SAVE_DIRECTORY, saveDirectory );
        values.put( LaunchProperty.SERVER_SAVE_INTERVAL, form.saveInterval().toString() );
        values.put( LaunchProperty.SERVER_BACKUP_COUNT, form.backupCount().toString() );
        values.put( LaunchProperty.SERVER_BACKUP_SHORT, form.backupShort().toString() );
        values.put( LaunchProperty.SERVER_BACKUP_LONG, form.backupLong().toString() );
        return values;
    }

    private static @Nonnull Map< SystemPropertyIf, String > toGameValues( @Nonnull GameSettingsForm form ) {
        Map< SystemPropertyIf, String > values = new LinkedHashMap<>();
        WorldPreset preset = form.presetValue();
        values.put( LaunchProperty.WORLD_PRESET, preset == null ? "" : preset.getCliValue() );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            values.put( modifier.getProperty(), emptyIfNull( form.modifierValue( modifier ) ) );
        }
        for ( WorldKey key : WorldKey.values() ) {
            values.put( key.getProperty(), Boolean.toString( form.isKeyEnabled( key ) ) );
        }
        return values;
    }

    private @Nonnull String read( @Nonnull LaunchProperty property ) {
        return launchProperties.getValue( property );
    }

    private int readInt( @Nonnull LaunchProperty property ) {
        String value = read( property );
        try {
            return Integer.parseInt( value );
        } catch ( NumberFormatException exception ) {
            log.warn( "Launch profile setting '{}' is not a number; using its default", property.getName() );
            return Integer.parseInt( property.getDefaultValue() );
        }
    }

    private boolean readBoolean( @Nonnull LaunchProperty property ) {
        String value = read( property );
        if ( "true".equalsIgnoreCase( value ) || "false".equalsIgnoreCase( value ) ) {
            return Boolean.parseBoolean( value );
        }
        log.warn( "Launch profile setting '{}' is not true or false; using its default", property.getName() );
        return Boolean.parseBoolean( property.getDefaultValue() );
    }

    private @Nullable WorldPreset readPreset() {
        String value = read( LaunchProperty.WORLD_PRESET );
        if ( value.isEmpty() ) {
            return null;
        }
        Optional< WorldPreset > preset = WorldPreset.fromCliValue( value );
        if ( preset.isEmpty() ) {
            log.warn( "Launch profile setting '{}' names no preset; ignoring it", LaunchProperty.WORLD_PRESET.getName() );
        }
        return preset.orElse( null );
    }

    private @Nonnull Optional< String > readModifier( @Nonnull WorldModifier modifier ) {
        String value = read( modifier.getProperty() );
        if ( value.isEmpty() ) {
            return Optional.empty();
        }
        if ( !modifier.isAllowed( value ) ) {
            log.warn( "Launch profile setting '{}' holds an unknown value; ignoring it", modifier.getProperty().getName() );
            return Optional.empty();
        }
        return Optional.of( value );
    }

    private static boolean isBlank( @Nullable String value ) {
        return value == null || value.isBlank();
    }

    private static @Nonnull String emptyIfNull( @Nullable String value ) {
        return value == null ? "" : value;
    }
}