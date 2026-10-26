package com.github.pplociennik.vdeck;

import jakarta.annotation.Nonnull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static java.util.Objects.requireNonNull;

/**
 * Entry point of the vDeck web panel managing a local Valheim dedicated server.
 */
@SpringBootApplication
public class VdeckApplication {

    /**
     * Starts the panel's Spring context.
     *
     * @param args
     *         command line arguments passed to Spring Boot
     * @throws NullPointerException
     *         if args is null
     */
    public static void main( @Nonnull String[] args ) {
        requireNonNull( args, "args must not be null" );
        SpringApplication.run( VdeckApplication.class, args );
    }
}
