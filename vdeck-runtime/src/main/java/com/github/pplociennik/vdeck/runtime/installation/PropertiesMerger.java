package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Writes settings values into the layout of a settings file, keeping its comments, blank lines, section
 * separators and key order. Shared by the updater, which merges a template of the new version with the
 * user's file (AD-025), and by the panel, which saves its settings files without losing their layout.
 *
 * <p>Rules of {@link #merge}:
 *
 * <ul>
 *     <li>the new template defines the set of keys, their order and every comment;</li>
 *     <li>a key present in both keeps the user's value, including an empty optional value;</li>
 *     <li>a key only in the template is added with the template's default value;</li>
 *     <li>a key only in the user's file disappears.</li>
 * </ul>
 *
 * <p>{@link #update} applies the same rewrite to a single file but keeps every key: a key without a new
 * value keeps its line, and a new key missing from the file is appended at its end.
 *
 * <p>Both inputs are read as UTF-8. Output values are escaped like {@link Properties#store}, with every
 * character outside printable ASCII written as {@code \}{@code uXXXX}, so the result is plain ASCII.
 * Only key names are logged; values may be secrets such as the server password.
 */
@Log4j2
public final class PropertiesMerger {

    private static final int MAX_SETTINGS_BYTES = 256 * 1024;

    /**
     * Merges two settings texts without I/O.
     *
     * @param template
     *         settings file of the new version
     * @param user
     *         the user's current settings file, or {@code null} when the user has none
     * @return merged text and the names of added and removed keys
     * @throws NullPointerException
     *         if the template is null
     * @throws IllegalArgumentException
     *         if either text is not a valid properties file
     */
    @Nonnull
    public MergeResult merge( @Nonnull String template, @Nullable String user ) {
        requireNonNull( template, "template must not be null" );
        Properties userValues = load( user == null ? "" : user );
        Rewrite rewrite = rewrite( template, userValues );
        List< String > removed = new ArrayList<>();
        for ( String key : userValues.stringPropertyNames() ) {
            if ( !rewrite.layoutKeys().contains( key ) ) {
                removed.add( key );
            }
        }
        removed.sort( null );
        return new MergeResult( rewrite.text().toString(), List.copyOf( rewrite.keptLines() ),
                List.copyOf( removed ) );
    }

    /**
     * Writes new values into the layout of one settings file without I/O. Every key of {@code values}
     * present in the text gets its value in place; a key of the text without a new value keeps its line;
     * a key of {@code values} missing from the text is appended at its end, in key name order.
     *
     * @param text
     *         current contents of the settings file; empty for a file that does not exist yet
     * @param values
     *         values to write
     * @return the updated text
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the text is not a valid properties file
     */
    @Nonnull
    public String update( @Nonnull String text, @Nonnull Properties values ) {
        requireNonNull( text, "text must not be null" );
        requireNonNull( values, "values must not be null" );
        Rewrite rewrite = rewrite( text, values );
        List< String > appended = new ArrayList<>();
        for ( String key : values.stringPropertyNames() ) {
            if ( !rewrite.layoutKeys().contains( key ) ) {
                appended.add( key );
            }
        }
        appended.sort( null );
        for ( String key : appended ) {
            appendEntry( rewrite.text(), key, values.getProperty( key ), rewrite.separator() );
        }
        return rewrite.text().toString();
    }

    /**
     * Merges files and durably writes the result to a new target, leaving both inputs untouched.
     *
     * @param template
     *         settings file of the new version
     * @param user
     *         the user's settings file; a missing file means the template is used as it is
     * @param target
     *         file receiving the merged result
     * @throws NullPointerException
     *         if an argument is null
     * @throws IOException
     *         if a file is a link, too large, unreadable or not writable
     * @throws IllegalArgumentException
     *         if a file is not a valid properties file
     */
    public void mergeFiles( @Nonnull Path template, @Nonnull Path user, @Nonnull Path target ) throws IOException {
        requireNonNull( template, "template must not be null" );
        requireNonNull( user, "user must not be null" );
        requireNonNull( target, "target must not be null" );
        String userText = Files.exists( user, NOFOLLOW_LINKS ) ? read( user ) : null;
        MergeResult result = merge( read( template ), userText );
        Files.createDirectories( target.getParent() );
        DurableFiles.write( target, result.contents().getBytes( StandardCharsets.UTF_8 ) );
        log.info( "Merged settings {}: added keys {}, removed keys {}", user.getFileName(), result.addedKeys(),
                result.removedKeys() );
    }

    /**
     * Copies every line of the layout, replacing the value of each key found in {@code values}. A
     * repeated key keeps only its first line. Keys without a new value keep their original line.
     */
    private static Rewrite rewrite( String layout, Properties values ) {
        String separator = layout.contains( "\r\n" ) ? "\r\n" : "\n";
        StringBuilder text = new StringBuilder();
        Set< String > layoutKeys = new HashSet<>();
        List< String > keptLines = new ArrayList<>();
        for ( String logicalLine : logicalLines( layout, separator ) ) {
            String key = keyOf( logicalLine );
            if ( key == null ) {
                text.append( logicalLine ).append( separator );
            } else if ( !layoutKeys.add( key ) ) {
                continue;
            } else if ( values.containsKey( key ) ) {
                appendEntry( text, key, values.getProperty( key ), separator );
            } else {
                keptLines.add( key );
                text.append( logicalLine ).append( separator );
            }
        }
        return new Rewrite( text, separator, layoutKeys, keptLines );
    }

    private static void appendEntry( StringBuilder text, String key, String value, String separator ) {
        text.append( escape( key, true ) ).append( '=' ).append( escape( value, false ) ).append( separator );
    }

    private static String read( Path file ) throws IOException {
        if ( !Files.isRegularFile( file, NOFOLLOW_LINKS ) || Files.size( file ) > MAX_SETTINGS_BYTES ) {
            throw new IOException( "Unsafe or oversized settings file " + file.getFileName() );
        }
        return Files.readString( file, StandardCharsets.UTF_8 );
    }

    private static Properties load( String text ) {
        Properties properties = new Properties();
        try {
            properties.load( new StringReader( text ) );
        } catch ( IOException | IllegalArgumentException exception ) {
            throw new IllegalArgumentException( "Invalid settings file", exception );
        }
        return properties;
    }

    /**
     * Splits a text into logical lines: a line ending with an odd number of backslashes continues on the
     * next physical line, except comment lines, which never continue.
     */
    private static List< String > logicalLines( String text, String separator ) {
        List< String > lines = new ArrayList<>();
        StringBuilder current = null;
        for ( String physical : text.split( "\r\n|\n|\r", -1 ) ) {
            if ( current == null ) {
                current = new StringBuilder( physical );
                if ( isBlankOrComment( physical ) ) {
                    lines.add( physical );
                    current = null;
                    continue;
                }
            } else {
                current.append( separator ).append( physical );
            }
            if ( !endsWithContinuation( physical ) ) {
                lines.add( current.toString() );
                current = null;
            }
        }
        if ( current != null ) {
            lines.add( current.toString() );
        }
        // split(-1) yields a trailing empty element for an empty text or one ending with a line break.
        if ( !lines.isEmpty() && lines.getLast().isEmpty()
                && ( text.isEmpty() || text.endsWith( "\n" ) || text.endsWith( "\r" ) ) ) {
            lines.removeLast();
        }
        return lines;
    }

    private static boolean isBlankOrComment( String line ) {
        String stripped = line.stripLeading();
        return stripped.isEmpty() || stripped.charAt( 0 ) == '#' || stripped.charAt( 0 ) == '!';
    }

    private static boolean endsWithContinuation( String line ) {
        int backslashes = 0;
        for ( int index = line.length() - 1; index >= 0 && line.charAt( index ) == '\\'; index-- ) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static @Nullable String keyOf( String logicalLine ) {
        if ( isBlankOrComment( logicalLine ) ) {
            return null;
        }
        Properties single = load( logicalLine );
        return single.stringPropertyNames().stream().findFirst().orElse( null );
    }

    /**
     * Escapes a key or value the way {@link Properties#store} does with Unicode escapes enabled.
     */
    private static String escape( String text, boolean key ) {
        StringBuilder escaped = new StringBuilder( text.length() + 8 );
        for ( int index = 0; index < text.length(); index++ ) {
            char character = text.charAt( index );
            switch ( character ) {
                case ' ' -> escaped.append( index == 0 || key ? "\\ " : " " );
                case '\t' -> escaped.append( "\\t" );
                case '\n' -> escaped.append( "\\n" );
                case '\r' -> escaped.append( "\\r" );
                case '\f' -> escaped.append( "\\f" );
                case '\\', '=', ':', '#', '!' -> escaped.append( '\\' ).append( character );
                default -> {
                    if ( character < 0x20 || character > 0x7e ) {
                        escaped.append( "\\u%04X".formatted( ( int ) character ) );
                    } else {
                        escaped.append( character );
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Merged settings text with the names of the keys that changed membership.
     *
     * @param contents
     *         merged text
     * @param addedKeys
     *         keys taken from the template because the user had none
     * @param removedKeys
     *         keys of the user's file dropped because the template no longer has them
     */
    public record MergeResult( @Nonnull String contents, @Nonnull List< String > addedKeys,
                               @Nonnull List< String > removedKeys ) {
    }

    /**
     * Rewritten layout: the text so far, its line separator, every key the layout holds and the keys whose
     * original line was kept because no new value was given.
     */
    private record Rewrite( StringBuilder text, String separator, Set< String > layoutKeys,
                            List< String > keptLines ) {
    }
}
