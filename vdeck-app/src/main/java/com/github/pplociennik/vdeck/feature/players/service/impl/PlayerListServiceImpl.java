package com.github.pplociennik.vdeck.feature.players.service.impl;

import com.github.pplociennik.vdeck.feature.launch.service.LaunchProfileService;
import com.github.pplociennik.vdeck.feature.players.PlayerListMessage;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerEntry;
import com.github.pplociennik.vdeck.feature.players.domain.PlayerList;
import com.github.pplociennik.vdeck.feature.players.dto.PlayerEntryForm;
import com.github.pplociennik.vdeck.feature.players.exc.PlayerListUpdateException;
import com.github.pplociennik.vdeck.feature.players.service.PlayerListService;
import com.github.pplociennik.vdeck.shared.exc.PathResolutionException;
import com.github.pplociennik.vdeck.shared.service.PathService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Reads and changes the player list files in the save directory of the launch profile.
 *
 * <p>The directory is taken from the published launch profile on every call and resolved through
 * {@link PathService}, so a saved change of the directory applies to the next request and the files never
 * lie outside the installation directory. Changes are serialized: each one reads the file, checks the
 * request against it and replaces the file whole, so two changes never lose each other's entry. Reads are
 * not serialized; the atomic replacement always leaves them a complete file.
 */
@Log4j2
@Service
class PlayerListServiceImpl implements PlayerListService {

    private final LaunchProfileService launchProfileService;
    private final PathService pathService;

    /**
     * Creates the service resolving the list files from the save directory of the launch profile.
     *
     * @param launchProfileService
     *         source of the configured save directory
     * @param pathService
     *         service resolving the save directory inside the installation directory
     * @throws NullPointerException
     *         if any service is null
     */
    PlayerListServiceImpl( @Nonnull LaunchProfileService launchProfileService, @Nonnull PathService pathService ) {
        this.launchProfileService = requireNonNull( launchProfileService, "launchProfileService must not be null" );
        this.pathService = requireNonNull( pathService, "pathService must not be null" );
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull List< PlayerEntry > getEntries( @Nonnull PlayerList playerList ) {
        requireNonNull( playerList, "playerList must not be null" );

        Path file = fileOf( playerList );
        try {
            List< PlayerEntry > entries = toEntries( PlayerListFile.readLines( file ) );
            log.debug( "Read {} entries of the player list {}", entries.size(), playerList.getFileName() );
            return entries;
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not read the player list " + playerList.getFileName(), exception );
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void addPlayer( @Nonnull PlayerList playerList, @Nonnull PlayerEntryForm form ) {
        requireNonNull( playerList, "playerList must not be null" );
        requireNonNull( form, "form must not be null" );

        if ( form.platform() == null || form.userId() == null ) {
            throw PlayerListUpdateException.rejected( PlayerListMessage.INVALID_INPUT, playerList, form );
        }
        if ( !form.platform().accepts( form.userId() ) ) {
            throw PlayerListUpdateException.rejected( PlayerListMessage.INVALID_USER_ID, playerList, form );
        }
        PlayerEntry entry = PlayerEntry.of( form.platform(), form.userId() );

        List< String > lines = readForChange( playerList, form );
        if ( findEntry( lines, entry.identifier() ).isPresent() ) {
            throw PlayerListUpdateException.rejected( PlayerListMessage.ALREADY_LISTED, playerList, form );
        }
        lines.add( entry.identifier() );
        writeForChange( playerList, form, lines );
        log.info( "Player {} added to the player list {}", entry.identifier(), playerList.getFileName() );
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void removePlayer( @Nonnull PlayerList playerList, @Nonnull String identifier ) {
        requireNonNull( playerList, "playerList must not be null" );
        requireNonNull( identifier, "identifier must not be null" );

        List< String > lines = readForChange( playerList, null );
        PlayerEntry entry = findEntry( lines, identifier ).orElseThrow(
                () -> PlayerListUpdateException.rejected( PlayerListMessage.NOT_LISTED, playerList, null ) );
        lines.removeIf( line -> isEntry( line, entry.identifier() ) );
        writeForChange( playerList, null, lines );
        log.info( "Player {} removed from the player list {}", entry.identifier(), playerList.getFileName() );
    }

    /**
     * Resolves the list file inside the save directory of the launch profile.
     *
     * @throws PathResolutionException
     *         if the save directory leaves the installation directory
     */
    private @Nonnull Path fileOf( @Nonnull PlayerList playerList ) {
        String saveDirectory = launchProfileService.getProfile().saveDirectory();
        return pathService.getAbsolutePath( saveDirectory ).resolve( playerList.getFileName() );
    }

    /**
     * Reads the lines of a list about to be changed, turning any failure into the exception of the change.
     */
    private @Nonnull List< String > readForChange( @Nonnull PlayerList playerList, @Nullable PlayerEntryForm form ) {
        try {
            return PlayerListFile.readLines( fileOf( playerList ) );
        } catch ( IOException | UncheckedIOException | PathResolutionException | IllegalArgumentException exception ) {
            throw PlayerListUpdateException.saveFailed( playerList, form, exception );
        }
    }

    /**
     * Writes the changed lines of a list, turning any failure into the exception of the change.
     */
    private void writeForChange( @Nonnull PlayerList playerList, @Nullable PlayerEntryForm form,
                                 @Nonnull List< String > lines ) {
        try {
            PlayerListFile.writeLines( fileOf( playerList ), lines );
        } catch ( IOException | UncheckedIOException | PathResolutionException | IllegalArgumentException exception ) {
            throw PlayerListUpdateException.saveFailed( playerList, form, exception );
        }
    }

    private static @Nonnull List< PlayerEntry > toEntries( @Nonnull List< String > lines ) {
        List< PlayerEntry > entries = new ArrayList<>();
        for ( String line : lines ) {
            PlayerEntry.parse( line ).ifPresent( entries::add );
        }
        return List.copyOf( entries );
    }

    private static @Nonnull Optional< PlayerEntry > findEntry( @Nonnull List< String > lines,
                                                               @Nonnull String identifier ) {
        for ( String line : lines ) {
            Optional< PlayerEntry > entry = PlayerEntry.parse( line );
            if ( entry.isPresent() && entry.get().identifier().equals( identifier ) ) {
                return entry;
            }
        }
        return Optional.empty();
    }

    private static boolean isEntry( @Nonnull String line, @Nonnull String identifier ) {
        return PlayerEntry.parse( line ).map( PlayerEntry::identifier ).filter( identifier::equals ).isPresent();
    }
}
