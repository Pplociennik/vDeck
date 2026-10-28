package com.github.pplociennik.vdeck.feature.releasenotes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the change list panel end to end: the build version from {@code build-info}, the Liquibase
 * table, the files shipped in {@code release-notes/}, the home page template and the acknowledgement
 * endpoint protected by Spring Security.
 *
 * <p>Version 0.0.1 is the first version with a change list, and its file always describes a change, so
 * acknowledging the version before it makes the panel show at least that list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext( classMode = DirtiesContext.ClassMode.AFTER_CLASS )
class ReleaseNotesIntegrationTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    /**
     * Class carried only by the change list panel.
     */
    private static final String PANEL_MARKER = "release-notes__version-title";

    @TempDir
    private static Path directory;

    @Autowired
    private MockMvc underTest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BuildProperties buildProperties;

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that the home page shows no panel once the installed version is acknowledged.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldShowNoPanel_whenInstalledVersionIsAcknowledged() throws Exception {
        // GIVEN
        acknowledgeOnly( installedVersion() );

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() ).andExpect( content().string( not( containsString( PANEL_MARKER ) ) ) );
    }

    /**
     * Verifies that after an update the home page shows the change lists with their version numbers and
     * translated texts, and a form closing the panel.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldShowPanelWithVersions_whenUpdatedFromOlderVersion() throws Exception {
        // GIVEN
        acknowledgeOnly( "0.0.0" );

        // WHEN
        var result = underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isOk() )
                .andExpect( content().string( containsString( PANEL_MARKER ) ) )
                .andExpect( content().string( containsString( "Wersja 0.0.1" ) ) )
                .andExpect( content().string( containsString( "Zaktualizowano vDeck do wersji " + installedVersion() ) ) )
                .andExpect( content().string( containsString( "action=\"/release-notes/acknowledge\"" ) ) )
                .andExpect( content().string( not( containsString( "releaseNotes." ) ) ) );
    }

    /**
     * Verifies that closing the panel stores the installed version, returns to the home page and hides
     * the panel from then on.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldHidePanel_whenAdministratorClosesIt() throws Exception {
        // GIVEN
        acknowledgeOnly( "0.0.0" );

        // WHEN
        var result = underTest.perform( post( "/release-notes/acknowledge" )
                .with( user( "admin" ).roles( "ADMIN" ) ).with( csrf() ) );

        // THEN
        result.andExpect( status().is3xxRedirection() ).andExpect( redirectedUrl( "/" ) );
        assertThat( acknowledgedVersions() ).contains( installedVersion() );
        underTest.perform( get( "/" ).with( user( "admin" ).roles( "ADMIN" ) ) )
                .andExpect( content().string( not( containsString( PANEL_MARKER ) ) ) );
    }

    /**
     * Verifies that closing the panel requires a CSRF token, like every state-changing request.
     *
     * @throws Exception
     *         if MockMvc cannot execute the request
     */
    @Test
    void shouldRejectAcknowledgement_whenCsrfTokenIsMissing() throws Exception {
        // GIVEN
        acknowledgeOnly( "0.0.0" );

        // WHEN
        var result = underTest.perform( post( "/release-notes/acknowledge" ).with( user( "admin" ).roles( "ADMIN" ) ) );

        // THEN
        result.andExpect( status().isForbidden() );
        assertThat( acknowledgedVersions() ).containsExactly( "0.0.0" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private void acknowledgeOnly( String version ) {
        jdbcTemplate.update( "DELETE FROM release_notes_acknowledgements" );
        jdbcTemplate.update( "INSERT INTO release_notes_acknowledgements (version) VALUES (?)", version );
    }

    private List< String > acknowledgedVersions() {
        return jdbcTemplate.queryForList( "SELECT version FROM release_notes_acknowledgements", String.class );
    }

    private String installedVersion() {
        return buildProperties.getVersion().replace( "-SNAPSHOT", "" );
    }

    /**
     * Supplies an isolated installation directory and SQLite file before the Spring context starts.
     *
     * @param registry
     *         dynamic application property registry
     */
    @DynamicPropertySource
    static void registerApplicationProperties( DynamicPropertyRegistry registry ) {
        registry.add( "vdeck.server-directory", directory::toString );
        registry.add( "spring.datasource.url", () -> "jdbc:sqlite:" + directory.resolve( "release-notes-test.db" ) );
    }
}
