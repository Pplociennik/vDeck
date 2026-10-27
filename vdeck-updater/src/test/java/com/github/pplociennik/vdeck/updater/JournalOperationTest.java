package com.github.pplociennik.vdeck.updater;

import com.github.pplociennik.vdeck.runtime.installation.DistributionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies the invariants of one journaled operation.
 */
class JournalOperationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String DIGEST = "a".repeat( 64 );

    private JournalOperation underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a pending file operation with an existing original.
     */
    @BeforeEach
    void setUp() {
        underTest = new JournalOperation( DistributionItem.README_MD, true, DIGEST, "b".repeat( 64 ),
                OperationStep.PENDING );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that changing the step keeps every other component.
     */
    @Test
    void shouldKeepIdentity_whenStepChanges() {
        // GIVEN
        // A pending operation.

        // WHEN
        JournalOperation applied = underTest.withStep( OperationStep.APPLIED );

        // THEN
        assertThat( applied.step() ).isEqualTo( OperationStep.APPLIED );
        assertThat( applied.withStep( OperationStep.PENDING ) ).isEqualTo( underTest );
    }

    /**
     * Verifies that directories never carry digests.
     */
    @Test
    void shouldThrowException_whenDirectoryHasDigest() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> new JournalOperation( DistributionItem.LIB, true, DIGEST, null,
                OperationStep.PENDING ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that files need the digest of the new content.
     */
    @Test
    void shouldThrowException_whenFileLacksNewDigest() {
        // GIVEN
        // No additional setup.

        // WHEN
        var failure = catchThrowable( () -> new JournalOperation( DistributionItem.MANUAL, false, null, "short",
                OperationStep.PENDING ) );

        // THEN
        assertThat( failure ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that the original digest matches the existence flag.
     */
    @Test
    void shouldThrowException_whenOriginalDigestContradictsExistence() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingDigest = catchThrowable( () -> new JournalOperation( DistributionItem.MANUAL, true, null, DIGEST,
                OperationStep.PENDING ) );
        var unexpectedDigest = catchThrowable( () -> new JournalOperation( DistributionItem.MANUAL, false, DIGEST,
                DIGEST, OperationStep.PENDING ) );

        // THEN
        assertThat( missingDigest ).isInstanceOf( IllegalArgumentException.class );
        assertThat( unexpectedDigest ).isInstanceOf( IllegalArgumentException.class );
    }

    /**
     * Verifies that the item and step are required.
     */
    @Test
    void shouldThrowException_whenComponentIsNull() {
        // GIVEN
        // No additional setup.

        // WHEN
        var missingItem = catchThrowable( () -> new JournalOperation( null, false, null, null, OperationStep.PENDING ) );
        var missingStep = catchThrowable( () -> underTest.withStep( null ) );

        // THEN
        assertThat( missingItem ).isInstanceOf( NullPointerException.class );
        assertThat( missingStep ).isInstanceOf( NullPointerException.class );
    }
}
