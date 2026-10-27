package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import com.github.pplociennik.vdeck.runtime.installation.DurableFiles;
import com.github.pplociennik.vdeck.runtime.installation.UpdateLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the planning of a transaction from the staged package.
 */
class UpdatePlanTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private Path directory;

    private UpdateLayout layout;

    private UpdatePlan underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates an installation of 1.0.0 and stages 1.1.0.
     */
    @BeforeEach
    void setUp() throws Exception {
        Path installation = Files.createDirectory( directory.toRealPath().resolve( "installation" ) );
        DistributionFixture.writeDistribution( installation, "1.0.0", "kept=custom\n" );
        layout = new UpdateLayout( installation );
        DistributionFixture.writeDistribution( layout.stagedDirectory(), "1.1.0", "kept=default\nadded=new\n" );
        underTest = new UpdatePlan( layout, new TransactionFiles( layout ), new PropertiesMerger() );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies one pending operation per item with digests of the originals and of the new contents, and
     * merged settings ready for replacement, without touching the installation.
     */
    @Test
    void shouldPlanEveryItem_whenStagedPackageIsComplete() throws Exception {
        // GIVEN
        Map< String, String > before = DistributionFixture.snapshot( layout.installation() );

        // WHEN
        List< JournalOperation > operations = underTest.prepare();

        // THEN
        assertThat( operations ).extracting( JournalOperation::item ).containsExactly( DistributionItem.values() );
        assertThat( operations ).allMatch( operation -> operation.step() == OperationStep.PENDING );
        assertThat( operations ).allMatch( JournalOperation::originalExists );
        Path merged = layout.mergedDirectory().resolve( DistributionItem.PANEL_SETTINGS.getPath() );
        assertThat( merged ).hasContent( "kept=custom\nadded=new\n" );
        assertThat( operations.get( DistributionItem.PANEL_SETTINGS.ordinal() ).newSha256() )
                .isEqualTo( DurableFiles.sha256( merged ) );
        assertThat( DistributionFixture.snapshot( layout.installation() ) ).isEqualTo( before );
    }

    /**
     * Verifies that an item missing in the installation is planned as a new file.
     */
    @Test
    void shouldPlanNewFile_whenInstallationLacksIt() throws Exception {
        // GIVEN
        Files.delete( layout.resolve( "README.txt" ) );

        // WHEN
        List< JournalOperation > operations = underTest.prepare();

        // THEN
        JournalOperation readme = operations.get( DistributionItem.README_TXT.ordinal() );
        assertThat( readme.originalExists() ).isFalse();
        assertThat( readme.originalSha256() ).isNull();
    }

    /**
     * Verifies that an incomplete staged package is refused before anything is planned.
     */
    @Test
    void shouldThrowException_whenStagedPackageIsIncomplete() throws Exception {
        // GIVEN
        Files.delete( layout.stagedDirectory().resolve( "lib/vdeck.jar" ) );

        // WHEN
        var failure = catchThrowable( () -> underTest.prepare() );

        // THEN
        assertThat( failure ).isInstanceOf( IOException.class ).hasMessageContaining( "lib/vdeck.jar" );
    }
}
