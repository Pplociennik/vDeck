package com.github.pplociennik.vdeck.updater;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Command line of the updater: {@code [--recover] [--handoff <id>] [-- <panel arguments>]}.
 * The start script runs {@code --recover} without a handoff; the launcher always passes a handoff.
 * Everything after {@code --} is forwarded unchanged to the launcher and then to the panel.
 *
 * @param recover
 *         whether only an interrupted transaction is recovered
 * @param handoffId
 *         handoff identifier when started by a launcher
 * @param panelArguments
 *         arguments forwarded to the panel
 */
record UpdaterArguments( boolean recover, @Nonnull Optional< String > handoffId,
                         @Nonnull List< String > panelArguments ) {

    /**
     * Copies the forwarded arguments.
     *
     * @throws NullPointerException
     *         if a reference component is null
     */
    UpdaterArguments {
        requireNonNull( handoffId, "handoffId must not be null" );
        panelArguments = List.copyOf( panelArguments );
    }

    /**
     * Parses the command line.
     *
     * @param arguments
     *         raw arguments
     * @return parsed arguments
     * @throws NullPointerException
     *         if the array or an element is null
     * @throws IllegalArgumentException
     *         if an option is unknown, repeated or lacks its value
     */
    static @Nonnull UpdaterArguments parse( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        boolean recover = false;
        String handoff = null;
        int index = 0;
        for ( ; index < arguments.length; index++ ) {
            String argument = requireNonNull( arguments[ index ], "argument must not be null" );
            if ( argument.equals( "--" ) ) {
                index++;
                break;
            }
            switch ( argument ) {
                case "--recover" -> {
                    if ( recover ) {
                        throw new IllegalArgumentException( "--recover is repeated" );
                    }
                    recover = true;
                }
                case "--handoff" -> {
                    if ( handoff != null || index + 1 >= arguments.length ) {
                        throw new IllegalArgumentException( "--handoff needs exactly one value" );
                    }
                    handoff = arguments[ ++index ];
                }
                default -> throw new IllegalArgumentException( "Unknown updater option " + argument );
            }
        }
        List< String > panel = Arrays.asList( arguments ).subList( Math.min( index, arguments.length ),
                arguments.length );
        return new UpdaterArguments( recover, Optional.ofNullable( handoff ), panel );
    }
}
