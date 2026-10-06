package com.github.pplociennik.vdeck.runtime.windows;

import jakarta.annotation.Nonnull;

import java.nio.file.Path;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Encodes an executable and its arguments into the single string {@code CreateProcessW} expects as
 * {@code lpCommandLine}.
 *
 * <p>This is the one place in vDeck allowed to concatenate a command from separate values, and it
 * exists only because the Win32 API offers no array form — the process is never handed to a shell, so
 * no shell metacharacter has any meaning here ({@code security.md} §4). Concentrating the encoding in
 * one tested class is what keeps that concession safe.
 *
 * <p>The encoding follows the rules {@code CommandLineToArgvW} uses when the child parses the string
 * back into an argument vector:
 *
 * <ul>
 *   <li>an argument containing no whitespace and no quote is emitted unchanged;</li>
 *   <li>an empty argument becomes {@code ""}, otherwise it would vanish;</li>
 *   <li>any other argument is wrapped in quotes;</li>
 *   <li>inside quotes, each run of backslashes directly before a quote is doubled and the quote is
 *       escaped, and a run of backslashes at the very end is doubled so it cannot escape the closing
 *       quote.</li>
 * </ul>
 *
 * <p>A value that survives this encoding is delivered to the child byte for byte, which matters for
 * world names and the server password.
 */
final class WindowsCommandLine {

    private WindowsCommandLine() {
    }

    /**
     * Builds the command line for one process. The executable is always quoted, because an
     * installation path routinely contains spaces and an unquoted one would silently split into
     * several arguments.
     *
     * @param executable
     *         absolute path of the program to run
     * @param arguments
     *         arguments to pass, without the program name; may be empty
     * @return command line ready to be handed to {@code CreateProcessW}
     * @throws NullPointerException
     *         if {@code executable}, {@code arguments} or any element of {@code arguments} is
     *         {@code null}
     */
    static @Nonnull String build( @Nonnull Path executable, @Nonnull List< String > arguments ) {
        requireNonNull( executable, "executable must not be null" );
        requireNonNull( arguments, "arguments must not be null" );

        StringBuilder commandLine = new StringBuilder();
        appendQuoted( commandLine, executable.toString() );
        for ( int index = 0; index < arguments.size(); index++ ) {
            String argument = requireNonNull( arguments.get( index ),
                    "arguments must not contain null at index " + index );
            commandLine.append( ' ' );
            appendArgument( commandLine, argument );
        }
        return commandLine.toString();
    }

    private static void appendArgument( @Nonnull StringBuilder commandLine, @Nonnull String argument ) {
        if ( argument.isEmpty() ) {
            commandLine.append( "\"\"" );
            return;
        }
        if ( needsQuoting( argument ) ) {
            appendQuoted( commandLine, argument );
            return;
        }
        commandLine.append( argument );
    }

    private static boolean needsQuoting( @Nonnull String argument ) {
        for ( int index = 0; index < argument.length(); index++ ) {
            char character = argument.charAt( index );
            if ( character == ' ' || character == '\t' || character == '\n' || character == '\u000B'
                    || character == '"' ) {
                return true;
            }
        }
        return false;
    }

    private static void appendQuoted( @Nonnull StringBuilder commandLine, @Nonnull String value ) {
        commandLine.append( '"' );
        int index = 0;
        while ( index < value.length() ) {
            int backslashes = 0;
            while ( index < value.length() && value.charAt( index ) == '\\' ) {
                index++;
                backslashes++;
            }
            if ( index == value.length() ) {
                // Trailing backslashes precede the closing quote, so they must not escape it.
                appendRepeated( commandLine, '\\', backslashes * 2 );
                break;
            }
            char character = value.charAt( index );
            if ( character == '"' ) {
                appendRepeated( commandLine, '\\', backslashes * 2 + 1 );
                commandLine.append( '"' );
            } else {
                appendRepeated( commandLine, '\\', backslashes );
                commandLine.append( character );
            }
            index++;
        }
        commandLine.append( '"' );
    }

    private static void appendRepeated( @Nonnull StringBuilder commandLine, char character, int count ) {
        for ( int index = 0; index < count; index++ ) {
            commandLine.append( character );
        }
    }
}