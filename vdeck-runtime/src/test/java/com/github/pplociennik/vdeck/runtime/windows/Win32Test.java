package com.github.pplociennik.vdeck.runtime.windows;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the native structure layouts match the sizes and offsets Windows documents for x64.
 *
 * <p>These assertions exist because a mistake in the padding of a structure does not fail loudly: the
 * call still succeeds and the operating system silently reads a field from the wrong offset. Checking
 * the arithmetic is the only cheap way to catch that, since the calls themselves cannot be exercised
 * without creating a real process.
 */
class Win32Test {

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that {@code STARTUPINFOW} is the 104 bytes the x64 ABI produces, so {@code cb} can be
     * set to the structure's real size.
     */
    @Test
    void shouldMatchDocumentedSize_whenStartupInfoIsLaidOut() {
        // GIVEN
        MemoryLayout underTest = Win32.STARTUPINFOW;

        // WHEN
        long result = underTest.byteSize();

        // THEN
        assertThat( result ).isEqualTo( 104L );
    }

    /**
     * Verifies that {@code PROCESS_INFORMATION} is 24 bytes, so the process identifier is read from
     * offset 16 rather than from padding.
     */
    @Test
    void shouldMatchDocumentedSize_whenProcessInformationIsLaidOut() {
        // GIVEN
        MemoryLayout underTest = Win32.PROCESS_INFORMATION;

        // WHEN
        long result = underTest.byteSize();

        // THEN
        assertThat( result ).isEqualTo( 24L );
    }

    /**
     * Verifies that the extended job limit structure is 144 bytes, the size
     * {@code SetInformationJobObject} is told to expect.
     */
    @Test
    void shouldMatchDocumentedSize_whenJobLimitInformationIsLaidOut() {
        // GIVEN
        MemoryLayout underTest = Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION;

        // WHEN
        long result = underTest.byteSize();

        // THEN
        assertThat( result ).isEqualTo( 144L );
    }

    /**
     * Verifies that the constant used to write the kill-on-close flag points at
     * {@code BasicLimitInformation.LimitFlags} and not at a neighbouring field.
     */
    @Test
    void shouldPointAtLimitFlags_whenOffsetConstantIsUsed() {
        // GIVEN
        MemoryLayout underTest = Win32.JOBOBJECT_EXTENDED_LIMIT_INFORMATION;

        // WHEN
        long result = underTest.byteOffset( MemoryLayout.PathElement.groupElement( "LimitFlags" ) );

        // THEN
        assertThat( result ).isEqualTo( Win32.LIMIT_FLAGS_OFFSET );
    }

    /**
     * Verifies that the process identifier sits where the launcher reads it from.
     */
    @Test
    void shouldPlaceProcessIdAfterBothHandles_whenProcessInformationIsLaidOut() {
        // GIVEN
        MemoryLayout underTest = Win32.PROCESS_INFORMATION;

        // WHEN
        long result = underTest.byteOffset( MemoryLayout.PathElement.groupElement( "dwProcessId" ) );

        // THEN
        assertThat( result ).isEqualTo( 16L );
    }
}