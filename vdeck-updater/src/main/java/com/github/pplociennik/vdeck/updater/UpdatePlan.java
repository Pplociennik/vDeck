package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.PropertiesMerger;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Builds the operations of a transaction from the verified staged package, without changing the
 * installation (AD-024, AD-025). The plan belongs to the new version: the installed launcher only
 * prepares the package and never decides what is replaced.
 *
 * <p>Every {@link DistributionItem} becomes one operation. Settings are merged first into
 * {@code .vDeck/update/merged}, so their replacement is an ordinary journaled atomic rename. The
 * database, logs, snapshots, the recovery token, the executor and Valheim files are never planned.
 */
@Log4j2
final class UpdatePlan {

    private final UpdateLayout layout;

    private final TransactionFiles files;

    private final PropertiesMerger merger;

    /**
     * Creates the planner.
     *
     * @param layout
     *         update layout of the canonical installation
     * @param files
     *         file actions of the installation
     * @param merger
     *         settings merger
     */
    UpdatePlan( @Nonnull UpdateLayout layout, @Nonnull TransactionFiles files, @Nonnull PropertiesMerger merger ) {
        this.layout = requireNonNull( layout, "layout must not be null" );
        this.files = requireNonNull( files, "files must not be null" );
        this.merger = requireNonNull( merger, "merger must not be null" );
    }

    /**
     * Checks the staged package, merges the settings and lists pending operations with the digests that
     * later identify originals and new files.
     *
     * @return pending operations in application order
     * @throws IOException
     *         if the staged package is incomplete or a file cannot be read, merged or written
     */
    @Nonnull
    List< JournalOperation > prepare() throws IOException {
        List< String > missing = DistributionItem.missingFiles( layout.stagedDirectory() );
        if ( !missing.isEmpty() ) {
            throw new IOException( "The staged package is incomplete: " + missing );
        }
        DurableFiles.deleteTree( layout.installation(), layout.mergedDirectory() );
        List< JournalOperation > operations = new ArrayList<>();
        for ( DistributionItem item : DistributionItem.values() ) {
            operations.add( plan( item ) );
        }
        log.info( "Planned {} operations", operations.size() );
        return List.copyOf( operations );
    }

    private JournalOperation plan( DistributionItem item ) throws IOException {
        boolean originalExists = files.exists( files.installed( item ) );
        if ( item.getKind() == DistributionItem.Kind.DIRECTORY ) {
            return new JournalOperation( item, originalExists, null, null, OperationStep.PENDING );
        }
        if ( item.getKind() == DistributionItem.Kind.SETTINGS ) {
            merger.mergeFiles( layout.stagedDirectory().resolve( item.getPath() ), files.installed( item ),
                    files.source( item ) );
        }
        String original = originalExists ? DurableFiles.sha256( files.installed( item ) ) : null;
        return new JournalOperation( item, originalExists, original, DurableFiles.sha256( files.source( item ) ),
                OperationStep.PENDING );
    }
}
