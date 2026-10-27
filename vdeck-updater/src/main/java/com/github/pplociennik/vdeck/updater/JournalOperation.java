package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * One journaled replacement of a distribution item. Hashes identify file contents during recovery;
 * directories are identified by their location, because every move happens on one volume.
 *
 * @param item
 *         replaced distribution item
 * @param originalExists
 *         whether the installation held the item before the transaction
 * @param originalSha256
 *         digest of the original file; {@code null} for directories and missing originals
 * @param newSha256
 *         digest of the new file; {@code null} for directories
 * @param step
 *         durable progress of the operation
 */
record JournalOperation( @Nonnull DistributionItem item, boolean originalExists, @Nullable String originalSha256,
                         @Nullable String newSha256, @Nonnull OperationStep step ) {

    private static final Pattern SHA_256 = Pattern.compile( "[0-9a-f]{64}" );

    /**
     * Validates the combination of kind, existence and digests.
     *
     * @throws NullPointerException
     *         if the item or step is null
     * @throws IllegalArgumentException
     *         if the digests do not match the kind of the item and the existence of the original
     */
    JournalOperation {
        requireNonNull( item, "item must not be null" );
        requireNonNull( step, "step must not be null" );
        boolean directory = item.getKind() == DistributionItem.Kind.DIRECTORY;
        if ( directory && ( originalSha256 != null || newSha256 != null ) ) {
            throw new IllegalArgumentException( "Directory operations carry no digests" );
        }
        if ( !directory && !isDigest( newSha256 ) ) {
            throw new IllegalArgumentException( "File operations need the digest of the new file" );
        }
        if ( !directory && originalExists != isDigest( originalSha256 ) ) {
            throw new IllegalArgumentException( "An existing original file needs its digest, a missing one none" );
        }
    }

    /**
     * Returns the same operation at another step.
     *
     * @param next
     *         new step
     * @return updated copy
     * @throws NullPointerException
     *         if the step is null
     */
    @Nonnull
    JournalOperation withStep( @Nonnull OperationStep next ) {
        return new JournalOperation( item, originalExists, originalSha256, newSha256, next );
    }

    private static boolean isDigest( @Nullable String value ) {
        return value != null && SHA_256.matcher( value ).matches();
    }
}
