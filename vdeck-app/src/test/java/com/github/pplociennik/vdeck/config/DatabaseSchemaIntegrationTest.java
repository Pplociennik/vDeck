package com.github.pplociennik.vdeck.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Liquibase creates the panel schema at startup and that the schema matches every JPA
 * entity: Hibernate validates the mapping instead of generating tables, so a missing or mismatched
 * changeset stops the context.
 */
@SpringBootTest
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class DatabaseSchemaIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    @TempDir
    private static Path directory;

    @Autowired
    private JdbcTemplate underTest;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /** The changelog is recorded as applied and the startup account is stored in its table. */
    @Test
    void shouldApplyChangelog_whenPanelStartsWithEmptyDatabase() {
        // GIVEN
        String changelogQuery = "SELECT id FROM DATABASECHANGELOG";
        // WHEN
        var appliedChangesets = underTest.queryForList( changelogQuery, String.class );
        Integer storedUsers = underTest.queryForObject( "SELECT COUNT(*) FROM panel_users", Integer.class );
        // THEN
        assertThat( appliedChangesets ).contains( "VDEC-24-create-panel-users" );
        assertThat( storedUsers ).isEqualTo( 1 );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    /**
     * Supplies an isolated installation and SQLite file and makes Hibernate validate the Liquibase schema.
     *
     * @param registry
     *         dynamic application property registry
     */
    @DynamicPropertySource
    static void registerApplicationProperties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "schema-test.db" ) );
        registry.add( "spring.jpa.hibernate.ddl-auto", () -> "validate" );
    }
}
