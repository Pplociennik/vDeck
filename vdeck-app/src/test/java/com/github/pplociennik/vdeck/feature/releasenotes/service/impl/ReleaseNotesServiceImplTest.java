package com.github.pplociennik.vdeck.feature.releasenotes.service.impl;

import com.github.pplociennik.vdeck.feature.releasenotes.ReleaseNotesMessage;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotes;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesAcknowledgement;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesCategory;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesEntry;
import com.github.pplociennik.vdeck.feature.releasenotes.domain.ReleaseNotesSection;
import com.github.pplociennik.vdeck.feature.releasenotes.exc.ReleaseNotesException;
import com.github.pplociennik.vdeck.feature.releasenotes.persistence.ReleaseNotesAcknowledgementRepository;
import com.github.pplociennik.vdeck.runtime.installation.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies that {@link ReleaseNotesServiceImpl} selects the change lists of the versions installed since
 * the last acknowledgement and stores acknowledgements of the installed version.
 */
@ExtendWith( MockitoExtension.class )
class ReleaseNotesServiceImplTest {

    // =================================================================================================
    // FIELDS
    // =================================================================================================

    private static final String INSTALLED = "0.3.0";

    @Mock
    private ReleaseNotesAcknowledgementRepository acknowledgementRepository;

    @Mock
    private ReleaseNotesReader releaseNotesReader;

    private ReleaseNotesServiceImpl underTest;

    // =================================================================================================
    // BEFORE METHODS
    // =================================================================================================

    /**
     * Creates a service whose build is the installed release; tests of other builds replace it.
     */
    @BeforeEach
    void setUp() {
        underTest = serviceForBuildVersion( INSTALLED );
    }

    // =================================================================================================
    // TESTS
    // =================================================================================================

    /**
     * Verifies that nothing is shown and no file is read once the installed version is acknowledged.
     */
    @Test
    void shouldReturnEmptyList_whenInstalledVersionIsAcknowledged() {
        // GIVEN
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( true );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).isEmpty();
        verifyNoInteractions( releaseNotesReader );
    }

    /**
     * Verifies that after skipping versions every change list newer than the last acknowledged version and
     * not newer than the installed one is shown, newest first.
     */
    @Test
    void shouldReturnSkippedVersions_whenOlderVersionIsAcknowledged() {
        // GIVEN
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( false );
        given( acknowledgementRepository.findAll() ).willReturn( List.of(
                acknowledgement( 0, 0, 9 ), acknowledgement( 0, 1, 0 ) ) );
        given( releaseNotesReader.readAll() ).willReturn( List.of(
                notes( 0, 4, 0 ), notes( 0, 3, 0 ), notes( 0, 2, 0 ), notes( 0, 1, 0 ), notes( 0, 0, 9 ) ) );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version )
                .containsExactly( new ReleaseVersion( 0, 3, 0 ), new ReleaseVersion( 0, 2, 0 ) );
    }

    /**
     * Verifies that without any acknowledgement - an update from a version that did not record them - only
     * the installed version's change list is shown.
     */
    @Test
    void shouldReturnOnlyInstalledVersion_whenNothingWasAcknowledged() {
        // GIVEN
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( false );
        given( acknowledgementRepository.findAll() ).willReturn( List.of() );
        given( releaseNotesReader.readAll() ).willReturn( List.of( notes( 0, 3, 0 ), notes( 0, 2, 0 ) ) );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly( new ReleaseVersion( 0, 3, 0 ) );
    }

    /**
     * Verifies that a stored value that is no release version does not count as the last acknowledgement.
     */
    @Test
    void shouldIgnoreAcknowledgement_whenStoredVersionIsInvalid() {
        // GIVEN
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( false );
        ReleaseNotesAcknowledgement invalid = new ReleaseNotesAcknowledgement() {
            @Override
            public String getVersion() {
                return "9.9";
            }
        };
        given( acknowledgementRepository.findAll() ).willReturn( List.of( invalid, acknowledgement( 0, 1, 0 ) ) );
        given( releaseNotesReader.readAll() ).willReturn( List.of( notes( 0, 3, 0 ), notes( 0, 2, 0 ) ) );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version )
                .containsExactly( new ReleaseVersion( 0, 3, 0 ), new ReleaseVersion( 0, 2, 0 ) );
    }

    /**
     * Verifies that a change list without any change is left out, so the panel never shows an empty
     * version.
     */
    @Test
    void shouldSkipChangeList_whenItHasNoChanges() {
        // GIVEN
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( false );
        given( acknowledgementRepository.findAll() ).willReturn( List.of( acknowledgement( 0, 1, 0 ) ) );
        given( releaseNotesReader.readAll() ).willReturn( List.of(
                new ReleaseNotes( new ReleaseVersion( 0, 3, 0 ), List.of() ), notes( 0, 2, 0 ) ) );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly( new ReleaseVersion( 0, 2, 0 ) );
    }

    /**
     * Verifies that a development build follows the change list of the version in progress.
     */
    @Test
    void shouldUseVersionInProgress_whenBuildIsSnapshot() {
        // GIVEN
        underTest = serviceForBuildVersion( "0.3.0-SNAPSHOT" );
        given( acknowledgementRepository.existsById( INSTALLED ) ).willReturn( false );
        given( acknowledgementRepository.findAll() ).willReturn( List.of() );
        given( releaseNotesReader.readAll() ).willReturn( List.of( notes( 0, 3, 0 ) ) );

        // WHEN
        var result = underTest.findUnacknowledged();

        // THEN
        assertThat( result ).extracting( ReleaseNotes::version ).containsExactly( new ReleaseVersion( 0, 3, 0 ) );
    }

    /**
     * Verifies that without build information the feature is off: nothing is shown or stored.
     */
    @Test
    void shouldDoNothing_whenBuildInformationIsMissing() {
        // GIVEN
        underTest = new ReleaseNotesServiceImpl( acknowledgementRepository, releaseNotesReader, null );

        // WHEN
        var result = underTest.findUnacknowledged();
        underTest.acknowledgeInstalled();

        // THEN
        assertThat( result ).isEmpty();
        verifyNoInteractions( acknowledgementRepository, releaseNotesReader );
    }

    /**
     * Verifies that a build version that is no release version turns the feature off.
     *
     * @param buildVersion
     *         build version that cannot be read as a release
     */
    @ParameterizedTest( name = "[{index}] buildVersion={0}" )
    @ValueSource( strings = { "1.2", "1.2.3-rc1", "unknown" } )
    void shouldDoNothing_whenBuildVersionIsNoRelease( String buildVersion ) {
        // GIVEN
        underTest = serviceForBuildVersion( buildVersion );

        // WHEN
        var result = underTest.findUnacknowledged();
        underTest.acknowledgeInstalled();

        // THEN
        assertThat( result ).isEmpty();
        verifyNoInteractions( acknowledgementRepository, releaseNotesReader );
    }

    /**
     * Verifies that acknowledging stores the installed version.
     */
    @Test
    void shouldStoreInstalledVersion_whenAcknowledged() {
        // GIVEN
        ArgumentCaptor< ReleaseNotesAcknowledgement > captor = ArgumentCaptor.forClass( ReleaseNotesAcknowledgement.class );

        // WHEN
        underTest.acknowledgeInstalled();

        // THEN
        verify( acknowledgementRepository ).save( captor.capture() );
        assertThat( captor.getValue().getVersion() ).isEqualTo( INSTALLED );
    }

    /**
     * Verifies that a persistence failure ends the acknowledgement with the process exception, keeping its
     * cause.
     */
    @Test
    void shouldThrowProcessException_whenAcknowledgementCannotBeStored() {
        // GIVEN
        var failure = new DataAccessResourceFailureException( "database locked" );
        given( acknowledgementRepository.save( any() ) ).willThrow( failure );

        // WHEN
        var exception = catchThrowable( () -> underTest.acknowledgeInstalled() );

        // THEN
        assertThat( exception ).isInstanceOf( ReleaseNotesException.class ).hasCause( failure );
        assertThat( ( (ReleaseNotesException) exception ).getMessageKey() )
                .isEqualTo( ReleaseNotesMessage.ACKNOWLEDGEMENT_FAILED );
    }

    /**
     * Verifies that a missing repository is rejected.
     */
    @Test
    void shouldThrowException_whenRepositoryIsNull() {
        // GIVEN
        // no repository

        // WHEN
        var exception = catchThrowable( () -> new ReleaseNotesServiceImpl( null, releaseNotesReader, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class )
                .hasMessageContaining( "acknowledgementRepository" );
    }

    /**
     * Verifies that a missing reader is rejected.
     */
    @Test
    void shouldThrowException_whenReaderIsNull() {
        // GIVEN
        // no reader

        // WHEN
        var exception = catchThrowable( () -> new ReleaseNotesServiceImpl( acknowledgementRepository, null, null ) );

        // THEN
        assertThat( exception ).isInstanceOf( NullPointerException.class ).hasMessageContaining( "releaseNotesReader" );
    }

    // =================================================================================================
    // HELPER METHODS
    // =================================================================================================

    private ReleaseNotesServiceImpl serviceForBuildVersion( String version ) {
        Properties properties = new Properties();
        properties.setProperty( "version", version );
        return new ReleaseNotesServiceImpl( acknowledgementRepository, releaseNotesReader,
                new BuildProperties( properties ) );
    }

    private static ReleaseNotesAcknowledgement acknowledgement( int major, int minor, int patch ) {
        return new ReleaseNotesAcknowledgement( new ReleaseVersion( major, minor, patch ) );
    }

    private static ReleaseNotes notes( int major, int minor, int patch ) {
        return new ReleaseNotes( new ReleaseVersion( major, minor, patch ), List.of( new ReleaseNotesSection(
                ReleaseNotesCategory.FEATURES, List.of( new ReleaseNotesEntry( "Zmiana", "Opis." ) ) ) ) );
    }
}
