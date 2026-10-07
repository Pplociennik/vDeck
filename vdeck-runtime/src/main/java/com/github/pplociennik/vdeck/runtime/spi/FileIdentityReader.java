package com.github.pplociennik.vdeck.runtime.spi;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.Path;

/** Reads a stable filesystem identity, independently of optional Java file keys and creation times. */
@FunctionalInterface
public interface FileIdentityReader {

    /**
     * Identifies the existing file without preventing concurrent writes or replacement.
     *
     * @param file
     *         absolute file to inspect
     * @return nonempty volume and file identifier
     * @throws IOException
     *         if a reliable identity cannot be read; implementations must fail closed
     * @throws NullPointerException
     *         if the file is null
     */
    @Nonnull String identify( @Nonnull Path file ) throws IOException;
}
