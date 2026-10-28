package com.github.pplociennik.vdeck.feature.releasenotes.service.impl;

import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesCategory;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesEntry;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesSection;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Parses one {@code release-notes/<version>.md} file. The format is a strict subset of Markdown, so the
 * same file renders on the GitHub release page and is read here without a Markdown library:
 *
 * <pre>
 * ## Nowości
 * - **One-line title**
 *   Very short description in one indented line.
 *
 * ## Poprawki
 * - **…**
 *   …
 * </pre>
 *
 * <p>Sections are optional but must follow the {@link ReleaseNotesCategory} order, each at most once.
 * Every entry has a title of at most {@value #MAX_TITLE_LENGTH} characters and exactly one description
 * line of at most {@value #MAX_DESCRIPTION_LENGTH} characters. Blank lines are ignored; any other line is
 * an error. A section without entries is valid and left out of the result, so a new version's file may
 * hold only the headings.
 *
 * <p>One instance holds the state of one parse and is never shared.
 */
final class ReleaseNotesParser {

    /**
     * Longest entry title in characters; a longer title stops being a one-line headline.
     */
    static final int MAX_TITLE_LENGTH = 80;

    /**
     * Longest entry description in characters, enough for one or two short sentences.
     */
    static final int MAX_DESCRIPTION_LENGTH = 200;

    /**
     * Prefix of a section heading line.
     */
    private static final String HEADING_PREFIX = "## ";

    /**
     * Prefix of an entry title line.
     */
    private static final String ENTRY_PREFIX = "- ";

    /**
     * Complete entry title line: a list item holding only the bold title.
     */
    private static final Pattern ENTRY_TITLE = Pattern.compile( "- \\*\\*([^*]+)\\*\\*" );

    /**
     * Indentation that continues a list item, marking the description line of the entry above.
     */
    private static final String DESCRIPTION_INDENT = "  ";

    /**
     * Byte order mark an editor may write at the start of a UTF-8 file.
     */
    private static final String BYTE_ORDER_MARK = "﻿";

    private final List< ReleaseNotesSection > sections = new ArrayList<>();

    private final List< ReleaseNotesEntry > entries = new ArrayList<>();

    /**
     * Category of the section being read, or {@code null} before the first heading.
     */
    private @Nullable ReleaseNotesCategory category;

    /**
     * Title of the entry waiting for its description line, or {@code null} when no entry is open.
     */
    private @Nullable String pendingTitle;

    /**
     * Number of the line being read, starting at 1, reported in errors.
     */
    private int lineNumber;

    private ReleaseNotesParser() {
    }

    /**
     * Parses the content of one change list file.
     *
     * @param version
     *         version the file belongs to, taken from its name
     * @param content
     *         whole file content
     * @return change list holding only the sections with entries
     * @throws IllegalArgumentException
     *         if the content breaks the format; the message names the line
     * @throws NullPointerException
     *         if an argument is {@code null}
     */
    static @Nonnull ReleaseNotes parse( @Nonnull ReleaseVersion version, @Nonnull String content ) {
        requireNonNull( version, "version must not be null" );
        requireNonNull( content, "content must not be null" );

        ReleaseNotesParser parser = new ReleaseNotesParser();
        String text = content.startsWith( BYTE_ORDER_MARK ) ? content.substring( BYTE_ORDER_MARK.length() ) : content;
        for ( String line : text.lines().toList() ) {
            parser.accept( line.stripTrailing() );
        }
        parser.finish();
        return new ReleaseNotes( version, parser.sections );
    }

    private void accept( String line ) {
        lineNumber++;
        if ( line.isEmpty() ) {
            return;
        }
        if ( line.startsWith( HEADING_PREFIX ) ) {
            startSection( line.substring( HEADING_PREFIX.length() ).strip() );
        } else if ( line.startsWith( ENTRY_PREFIX ) ) {
            startEntry( line );
        } else if ( line.startsWith( DESCRIPTION_INDENT ) ) {
            completeEntry( line.strip() );
        } else {
            throw malformed( "expected a '## ' heading, a '- **Title**' entry or an indented description" );
        }
    }

    private void startSection( String heading ) {
        requireNoPendingEntry();
        ReleaseNotesCategory next = ReleaseNotesCategory.fromHeading( heading )
                .orElseThrow( () -> malformed( "unknown heading '" + heading + "'" ) );
        if ( category != null && next.compareTo( category ) <= 0 ) {
            throw malformed( "heading '" + heading + "' is repeated or out of order" );
        }
        closeSection();
        category = next;
    }

    private void startEntry( String line ) {
        if ( category == null ) {
            throw malformed( "entry before the first heading" );
        }
        requireNoPendingEntry();
        Matcher matcher = ENTRY_TITLE.matcher( line );
        if ( !matcher.matches() ) {
            throw malformed( "entry title must be written as '- **Title**'" );
        }
        pendingTitle = requireText( matcher.group( 1 ).strip(), MAX_TITLE_LENGTH, "title" );
    }

    private void completeEntry( String description ) {
        if ( pendingTitle == null ) {
            throw malformed( "description without an entry title above it" );
        }
        entries.add( new ReleaseNotesEntry( pendingTitle,
                requireText( description, MAX_DESCRIPTION_LENGTH, "description" ) ) );
        pendingTitle = null;
    }

    private void finish() {
        requireNoPendingEntry();
        closeSection();
    }

    private void requireNoPendingEntry() {
        if ( pendingTitle != null ) {
            throw malformed( "entry '" + pendingTitle + "' has no description line" );
        }
    }

    private void closeSection() {
        if ( category != null && !entries.isEmpty() ) {
            sections.add( new ReleaseNotesSection( category, entries ) );
        }
        entries.clear();
    }

    private String requireText( String text, int maxLength, String name ) {
        if ( text.isEmpty() ) {
            throw malformed( "empty " + name );
        }
        if ( text.length() > maxLength ) {
            throw malformed( name + " longer than " + maxLength + " characters" );
        }
        return text;
    }

    private IllegalArgumentException malformed( String reason ) {
        return new IllegalArgumentException( "Line " + lineNumber + ": " + reason );
    }
}
