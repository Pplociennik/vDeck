package com.github.pplociennik.vdeck.feature.server.service.impl;

import static com.github.pplociennik.vdeck.shared.PropertyReader.read;

import com.github.pplociennik.vdeck.feature.launch.domain.LaunchConfiguration;
import com.github.pplociennik.vdeck.feature.launch.domain.LaunchProfile;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldKey;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldModifier;
import com.github.pplociennik.vdeck.feature.launch.domain.WorldPreset;
import com.github.pplociennik.vdeck.feature.server.service.LaunchCommandService;
import com.github.pplociennik.vdeck.feature.server.service.ServerService;
import com.github.pplociennik.vdeck.runtime.api.LaunchSpec;
import com.github.pplociennik.vdeck.shared.VdeckProperty;
import com.github.pplociennik.vdeck.shared.service.PathService;
import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Builds the server command line from one captured configuration. The mirror script is rendered
 * directly from that specification, so it describes the same launch without another data wrapper.
 *
 * <p>The options are assembled in the order of the two settings screens — identity and access,
 * networking, world and saving, world rules — followed by the log file the panel decides on. The
 * captured server password is placed in the specification, but never in the script and never in a
 * log entry.
 */
@Log4j2
@Service
class LaunchCommandServiceImpl implements LaunchCommandService {

    /**
     * Environment variable the mirror script reads the password from, so the file holds no secret.
     */
    static final String PASSWORD_VARIABLE = "VDECK_SERVER_PASSWORD";

    /**
     * Steam application identifier of the Valheim dedicated server, used when the publisher's script
     * cannot be read. The server refuses to start without it in its environment.
     */
    static final String DEFAULT_STEAM_APP_ID = "892970";

    private static final String STEAM_APP_ID_VARIABLE = "SteamAppId";

    private static final Pattern STEAM_APP_ID_LINE =
            Pattern.compile( "^\\s*set\\s+SteamAppId\\s*=\\s*(\\S+)\\s*$", Pattern.CASE_INSENSITIVE );

    private final SystemPropertiesService systemPropertiesService;

    private final PathService pathService;

    private final Function< Path, Stream< String > > scriptLines;

    /**
     * Creates the service assembling the launch command.
     *
     * @param systemPropertiesService
     *         service reading the launch profile and the panel's own settings
     * @param pathService
     *         service resolving the installation directory and the world directory
     */
    @Autowired
    LaunchCommandServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                              @Nonnull PathService pathService ) {
        this( systemPropertiesService, pathService, LaunchCommandServiceImpl::readScriptLines );
    }

    /**
     * Creates the builder with a publisher-script reader whose returned stream is closed by this service.
     *
     * @param systemPropertiesService
     *         panel settings
     * @param pathService
     *         installation path resolver
     * @param scriptLines
     *         reader throwing UncheckedIOException on I/O failure
     * @throws NullPointerException
     *         if any dependency is null
     */
    LaunchCommandServiceImpl( @Nonnull SystemPropertiesService systemPropertiesService,
                              @Nonnull PathService pathService,
                              @Nonnull Function< Path, Stream< String > > scriptLines ) {
        this.systemPropertiesService = requireNonNull( systemPropertiesService,
                "systemPropertiesService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
        this.scriptLines = requireNonNull( scriptLines, "scriptLines must not be null" );
    }

    @Override
    public @Nonnull LaunchSpec build( @Nonnull LaunchConfiguration configuration, @Nonnull Path logFile ) {
        requireNonNull( configuration, "configuration must not be null" );
        requireNonNull( logFile, "logFile must not be null" );

        Path baseDirectory = pathService.getBaseDirectory();
        Path executable = baseDirectory.resolve( ServerService.EXECUTABLE_NAME );

        List< String > arguments = arguments( configuration.profile(), configuration.password(), logFile );
        Map< String, String > environment = environment();

        return new LaunchSpec( executable, arguments, environment, baseDirectory, logFile );
    }

    @Override
    public @Nonnull String renderScript( @Nonnull LaunchSpec launchSpec ) {
        requireNonNull( launchSpec, "launchSpec must not be null" );
        StringBuilder script = new StringBuilder();
        script.append( "@echo off\r\n" )
                .append( "setlocal DisableDelayedExpansion\r\n" )
                .append( "chcp 65001 >nul\r\n" )
                .append( "REM Generated by vDeck. Do not edit - rewritten on every server start.\r\n" )
                .append( "REM It mirrors what the panel runs; the panel never runs this file.\r\n" )
                .append( "REM Settings come from .vDeck/config/valheim-server.properties\r\n" )
                .append( "set " ).append( STEAM_APP_ID_VARIABLE ).append( '=' )
                .append( launchSpec.environment().get( STEAM_APP_ID_VARIABLE ) ).append( "\r\n" );
        script.append( quote( launchSpec.executable().toString() ) );
        List< String > arguments = launchSpec.arguments();
        for ( int index = 0; index < arguments.size(); index++ ) {
            String option = arguments.get( index );
            script.append( ' ' ).append( quote( option ) );
            // Consume values with their option, so a name such as "-password" is never an option.
            int valueCount = switch ( option ) {
                case "-nographics", "-batchmode", "-crossplay" -> 0;
                case "-modifier" -> 2;
                default -> 1;
            };
            for ( int value = 0; value < valueCount; value++ ) {
                String argument = arguments.get( ++index );
                script.append( ' ' ).append( "-password".equals( option )
                        ? "\"%" + PASSWORD_VARIABLE + "%\"" : quote( argument ) );
            }
        }
        return script.append( "\r\n" ).toString();
    }

    private @Nonnull List< String > arguments( @Nonnull LaunchProfile profile, @Nonnull String password,
                                               @Nonnull Path logFile ) {
        List< String > arguments = new ArrayList<>();
        addHeadlessFlags( arguments );
        addIdentityAndAccess( arguments, profile, password );
        addNetworking( arguments, profile );
        addWorldAndSaving( arguments, profile );
        addWorldRules( arguments, profile );
        addOption( arguments, "-logFile", logFile.toString() );
        return arguments;
    }

    /**
     * Adds the options that make the server run without a window or a renderer. They are not settings:
     * a dedicated server is always headless.
     */
    private static void addHeadlessFlags( @Nonnull List< String > arguments ) {
        arguments.add( "-nographics" );
        arguments.add( "-batchmode" );
    }

    private static void addIdentityAndAccess( @Nonnull List< String > arguments,
                                              @Nonnull LaunchProfile profile, @Nonnull String password ) {
        addOption( arguments, "-name", profile.serverName() );
        addOptionalOption( arguments, "-password", password );
        addOption( arguments, "-public", profile.publicServer() ? "1" : "0" );
    }

    private static void addNetworking( @Nonnull List< String > arguments, @Nonnull LaunchProfile profile ) {
        addOption( arguments, "-port", Integer.toString( profile.port() ) );
        addFlag( arguments, "-crossplay", profile.crossplay() );
        addOptionalOption( arguments, "-instanceid", profile.instanceId() );
    }

    private void addWorldAndSaving( @Nonnull List< String > arguments, @Nonnull LaunchProfile profile ) {
        addOption( arguments, "-world", profile.worldName() );
        addOption( arguments, "-savedir", pathService.getAbsolutePath( profile.saveDirectory() ).toString() );
        addOption( arguments, "-saveinterval", Integer.toString( profile.saveInterval() ) );
        addOption( arguments, "-backups", Integer.toString( profile.backupCount() ) );
        addOption( arguments, "-backupshort", Integer.toString( profile.backupShort() ) );
        addOption( arguments, "-backuplong", Integer.toString( profile.backupLong() ) );
    }

    /**
     * Adds the world rules: the preset, then every modifier that has a value, then every enabled key.
     * A modifier left empty keeps the game's own default, so it is not passed at all.
     */
    private static void addWorldRules( @Nonnull List< String > arguments, @Nonnull LaunchProfile profile ) {
        addOptionalOption( arguments, "-preset", presetValue( profile.preset() ) );
        for ( WorldModifier modifier : WorldModifier.values() ) {
            addModifier( arguments, modifier, profile.modifiers().get( modifier ) );
        }
        for ( WorldKey key : WorldKey.values() ) {
            addFlagOption( arguments, "-setkey", key.getCliName(), profile.enabledKeys().contains( key ) );
        }
    }

    private static void addModifier( @Nonnull List< String > arguments, @Nonnull WorldModifier modifier,
                                     @Nullable String value ) {
        if ( value == null || value.isEmpty() ) {
            return;
        }
        arguments.add( "-modifier" );
        arguments.add( modifier.getCliName() );
        arguments.add( value );
    }

    private static @Nonnull String presetValue( @Nullable WorldPreset preset ) {
        return preset == null ? "" : preset.getCliValue();
    }

    private static void addOption( @Nonnull List< String > arguments, @Nonnull String option,
                                   @Nonnull String value ) {
        arguments.add( option );
        arguments.add( value );
    }

    /**
     * Adds an option only when it has a value, because an empty optional setting means the option is
     * not passed at all.
     */
    private static void addOptionalOption( @Nonnull List< String > arguments, @Nonnull String option,
                                           @Nullable String value ) {
        if ( value == null || value.isEmpty() ) {
            return;
        }
        addOption( arguments, option, value );
    }

    private static void addFlag( @Nonnull List< String > arguments, @Nonnull String flag, boolean enabled ) {
        if ( !enabled ) {
            return;
        }
        arguments.add( flag );
    }

    private static void addFlagOption( @Nonnull List< String > arguments, @Nonnull String option,
                                       @Nonnull String value, boolean enabled ) {
        if ( !enabled ) {
            return;
        }
        addOption( arguments, option, value );
    }

    /**
     * Builds the child's environment: the panel's own, plus the Steam application identifier the server
     * requires.
     */
    private @Nonnull Map< String, String > environment() {
        Map< String, String > environment = new LinkedHashMap<>( System.getenv() );
        environment.put( STEAM_APP_ID_VARIABLE, steamAppId() );
        return environment;
    }

    /**
     * Reads the Steam application identifier from the publisher's start script, so a change to it does
     * not need a new panel release. Falls back to the built-in value, loudly, when the script is
     * missing or says nothing about it.
     */
    private @Nonnull String steamAppId() {
        Path script = pathService.getBaseDirectory()
                .resolve( read( systemPropertiesService, VdeckProperty.FILE_NAME,
                        VdeckProperty.SERVER_START_SCRIPT ) );
        if ( !Files.isRegularFile( script ) ) {
            log.warn( "Start script {} not found, using the built-in Steam application identifier", script );
            return DEFAULT_STEAM_APP_ID;
        }
        try ( Stream< String > lines = scriptLines.apply( script ) ) {
            return lines.map( STEAM_APP_ID_LINE::matcher )
                    .filter( Matcher::matches )
                    .map( matcher -> matcher.group( 1 ) )
                    .findFirst()
                    .orElseGet( () -> missingSteamAppId( script ) );
        } catch ( UncheckedIOException exception ) {
            log.warn( "Could not read start script {}: {}", script, exception.getMessage() );
            return DEFAULT_STEAM_APP_ID;
        }
    }

    private static Stream< String > readScriptLines( Path script ) {
        try {
            return Files.lines( script, StandardCharsets.ISO_8859_1 );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( exception );
        }
    }

    private static @Nonnull String missingSteamAppId( @Nonnull Path script ) {
        log.warn( "Start script {} declares no SteamAppId, using the built-in one", script );
        return DEFAULT_STEAM_APP_ID;
    }

    /**
     * Protects literal percent signs against batch expansion and quotes whitespace and shell operators.
     * Delayed expansion is disabled in the script header, so exclamation marks remain literal.
     * Validated profile text contains no embedded double quotes.
     */
    private static @Nonnull String quote( @Nonnull String value ) {
        String escaped = value.replace( "%", "%%" );
        boolean needsQuoting = value.isEmpty() || value.chars()
                .anyMatch( character -> Character.isWhitespace( character ) || "&|<>^()".indexOf( character ) >= 0 );
        if ( !needsQuoting ) {
            return escaped;
        }
        // A quoted directory ending in backslashes must not escape the closing argument delimiter.
        int trailingSlashes = 0;
        for ( int index = escaped.length() - 1; index >= 0 && escaped.charAt( index ) == '\\'; index-- ) {
            trailingSlashes++;
        }
        return '"' + escaped + "\\".repeat( trailingSlashes ) + '"';
    }
}
