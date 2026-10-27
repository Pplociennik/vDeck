package com.github.pplociennik.vdeck.launcher;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Command line of the launcher: {@code [--after-update] [--handoff <id>] [-- <panel arguments>]}.
 * The start script passes only panel arguments; the updater adds {@code --after-update} and a handoff.
 * Everything after {@code --} is forwarded unchanged to the panel.
 *
 * @param afterUpdate
 *         whether an updater or recovery started this launcher; no update check follows
 * @param handoffId
 *         handoff identifier when started by the updater
 * @param panelArguments
 *         arguments forwarded to the panel
 */
record LauncherArguments( boolean afterUpdate, @Nonnull Optional< String > handoffId,
                         @Nonnull List< String > panelArguments ) {

    /**
     * Copies the forwarded arguments.
     *
     * @throws NullPointerException
     *         if a reference component is null
     */
    LauncherArguments {
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
    static @Nonnull LauncherArguments parse( @Nonnull String[] arguments ) {
        requireNonNull( arguments, "arguments must not be null" );
        boolean afterUpdate = false;
        String handoff = null;
        int index = 0;
        for ( ; index < arguments.length; index++ ) {
            String argument = requireNonNull( arguments[ index ], "argument must not be null" );
            if ( argument.equals( "--" ) ) {
                index++;
                break;
            }
            switch ( argument ) {
                case "--after-update" -> {
                    if ( afterUpdate ) {
                        throw new IllegalArgumentException( "--after-update is repeated" );
                    }
                    afterUpdate = true;
                }
                case "--handoff" -> {
                    if ( handoff != null || index + 1 >= arguments.length ) {
                        throw new IllegalArgumentException( "--handoff needs exactly one value" );
                    }
                    handoff = arguments[ ++index ];
                }
                default -> throw new IllegalArgumentException( "Unknown launcher option " + argument );
            }
        }
        List< String > panel = Arrays.asList( arguments ).subList( Math.min( index, arguments.length ),
                arguments.length );
        return new LauncherArguments( afterUpdate, Optional.ofNullable( handoff ), panel );
    }
}
