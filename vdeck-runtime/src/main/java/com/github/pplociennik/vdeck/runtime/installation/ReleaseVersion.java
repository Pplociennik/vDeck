package com.github.pplociennik.vdeck.runtime.installation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Release version {@code MAJOR.MINOR.PATCH} as tagged by AD-023, compared numerically. Pre-release and
 * build suffixes are not release versions: a {@code -SNAPSHOT} build never checks for updates, and a
 * release is installed only when it is strictly newer, so the launcher never downgrades.
 *
 * <p>Shared by the launcher, which compares the installed version with the latest GitHub release, and by
 * the panel, which orders the per-version change lists shown after an update.
 *
 * @param major
 *         major number
 * @param minor
 *         minor number
 * @param patch
 *         patch number
 */
public record ReleaseVersion( int major, int minor, int patch ) implements Comparable< ReleaseVersion > {

    /**
     * Exact release version: three dot-separated numbers without leading zeros, at most nine digits each
     * so every number fits an {@code int}.
     */
    private static final Pattern FORMAT = Pattern.compile( "(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})\\.(0|[1-9]\\d{0,8})" );

    /**
     * Numeric order of major, minor and patch numbers.
     */
    private static final Comparator< ReleaseVersion > ORDER = Comparator.comparingInt( ReleaseVersion::major )
            .thenComparingInt( ReleaseVersion::minor ).thenComparingInt( ReleaseVersion::patch );

    /**
     * Validates the numbers.
     *
     * @throws IllegalArgumentException
     *         if a number is negative
     */
    public ReleaseVersion {
        if ( major < 0 || minor < 0 || patch < 0 ) {
            throw new IllegalArgumentException( "Version numbers must not be negative" );
        }
    }

    /**
     * Parses an exact release version.
     *
     * @param text
     *         text such as {@code 1.2.3}
     * @return parsed version, or an empty optional for anything else, including {@code -SNAPSHOT} builds
     */
    public static @Nonnull Optional< ReleaseVersion > parse( @Nullable String text ) {
        if ( text == null ) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher( text );
        if ( !matcher.matches() ) {
            return Optional.empty();
        }
        return Optional.of( new ReleaseVersion( Integer.parseInt( matcher.group( 1 ) ),
                Integer.parseInt( matcher.group( 2 ) ), Integer.parseInt( matcher.group( 3 ) ) ) );
    }

    /**
     * Compares versions numerically.
     *
     * @param other
     *         version to compare with
     * @return negative, zero or positive as this version is older, equal or newer
     * @throws NullPointerException
     *         if the other version is null
     */
    @Override
    public int compareTo( @Nonnull ReleaseVersion other ) {
        return ORDER.compare( this, requireNonNull( other, "other must not be null" ) );
    }

    /**
     * Reports whether this version is strictly newer.
     *
     * @param other
     *         version to compare with, for example the installed one
     * @return {@code true} only for a strictly higher version
     * @throws NullPointerException
     *         if the other version is null
     */
    public boolean isNewerThan( @Nonnull ReleaseVersion other ) {
        return compareTo( other ) > 0;
    }

    /**
     * Returns the version text used in tags, asset names, manifests and change list file names.
     *
     * @return {@code MAJOR.MINOR.PATCH}
     */
    @Override
    public @Nonnull String toString() {
        return major + "." + minor + "." + patch;
    }
}
