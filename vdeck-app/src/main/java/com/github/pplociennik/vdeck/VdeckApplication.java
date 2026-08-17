package com.github.pplociennik.vdeck;

import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the vDeck web panel managing a local Valheim dedicated server.
 */
@SpringBootApplication
public class VdeckApplication {

    /**
     * Starts the vDeck panel.
     *
     * @param args
     *         command line arguments passed to Spring Boot
     */
    public static void main( @Nonnull String[] args ) {
        SpringApplication.run( VdeckApplication.class, args );
    }
}