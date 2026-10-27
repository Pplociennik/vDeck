package com.github.pplociennik.vdeck.launcher;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Reads the latest published release of vDeck from the GitHub REST API and validates its distribution
 * asset (AD-023, AD-024). Drafts and pre-releases are never returned by the {@code latest} endpoint and
 * are rejected again here.
 *
 * <p>The response is untrusted: the whole body, not only the headers, must arrive within
 * {@link #JSON_TIMEOUT} and the shared deadline, it is limited to {@link #MAX_RESPONSE_BYTES}, and the
 * release is accepted only with a tag {@code v<MAJOR.MINOR.PATCH>}, exactly one asset named
 * {@code vDeck-dist-<version>.zip}, a positive size of at most {@link #MAX_PACKAGE_BYTES}, a
 * {@code sha256:} digest and an HTTPS download URL of this repository on {@code github.com}. The
 * response body itself is never logged.
 */
@Log4j2
final class GitHubReleaseClient {

    /**
     * Endpoint of the latest release of the vDeck repository.
     */
    static final URI LATEST_RELEASE = URI.create( "https://api.github.com/repos/Pplociennik/vDeck/releases/latest" );

    /**
     * Limit for reading the complete API response.
     */
    static final Duration JSON_TIMEOUT = Duration.ofSeconds( 15 );

    /**
     * Largest accepted API response.
     */
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    /**
     * Largest accepted distribution package.
     */
    static final long MAX_PACKAGE_BYTES = 512L * 1024 * 1024;

    private static final String DOWNLOAD_PATH = "/Pplociennik/vDeck/releases/download/";

    private static final Pattern TAG = Pattern.compile( "v(\\d+\\.\\d+\\.\\d+)" );

    private static final Pattern DIGEST = Pattern.compile( "sha256:([0-9a-f]{64})" );

    private static final JsonFactory JSON = JsonFactory.builder().build();

    private final HttpClient client;

    private final URI endpoint;

    private final String userAgent;

    /**
     * Creates the client.
     *
     * @param client
     *         HTTP client following redirects without HTTPS-to-HTTP downgrades
     * @param endpoint
     *         latest-release endpoint, {@link #LATEST_RELEASE} outside tests
     * @param currentVersion
     *         installed version, sent in the {@code User-Agent} header
     */
    GitHubReleaseClient( @Nonnull HttpClient client, @Nonnull URI endpoint, @Nonnull ReleaseVersion currentVersion ) {
        this.client = requireNonNull( client, "client must not be null" );
        this.endpoint = requireNonNull( endpoint, "endpoint must not be null" );
        this.userAgent = "vDeck/" + requireNonNull( currentVersion, "currentVersion must not be null" );
    }

    /**
     * Fetches and validates the latest release. Blocks for at most {@link #JSON_TIMEOUT} and never past
     * the deadline.
     *
     * @param deadline
     *         shared preparation deadline
     * @return validated asset, or an empty optional when the repository has no published release
     * @throws NullPointerException
     *         if the deadline is null
     * @throws IOException
     *         if the request fails, times out, exceeds a limit or describes an invalid release
     */
    @Nonnull
    Optional< ReleaseAsset > fetchLatest( @Nonnull Deadline deadline ) throws IOException {
        requireNonNull( deadline, "deadline must not be null" );
        Duration limit = deadline.remainingAtMost( JSON_TIMEOUT );
        long readDeadline = System.nanoTime() + limit.toNanos();
        HttpRequest request = HttpRequest.newBuilder( endpoint ).timeout( positive( limit ) )
                .header( "Accept", "application/vnd.github+json" ).header( "X-GitHub-Api-Version", "2022-11-28" )
                .header( "User-Agent", userAgent ).GET().build();
        log.info( "Checking the latest vDeck release" );
        HttpResponse< InputStream > response = send( request );
        try ( InputStream body = response.body() ) {
            if ( response.statusCode() == 404 ) {
                log.info( "The repository has no published release" );
                return Optional.empty();
            }
            if ( response.statusCode() != 200 ) {
                throw new IOException( "GitHub API answered with status " + response.statusCode() );
            }
            byte[] contents = readBounded( body, Duration.ofNanos( Math.max( 0, readDeadline - System.nanoTime() ) ) );
            ReleaseAsset asset = toAsset( parse( contents ) );
            log.info( "Latest release is {} with asset {} of {} bytes", asset.version(), asset.name(), asset.size() );
            return Optional.of( asset );
        }
    }

    private HttpResponse< InputStream > send( HttpRequest request ) throws IOException {
        try {
            return client.send( request, HttpResponse.BodyHandlers.ofInputStream() );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException( "Release check was cancelled" );
        }
    }

    /**
     * Reads the whole body within the limit; a watchdog closes the stream when the time is up, which
     * also aborts a read blocked on a stalled connection.
     */
    private static byte[] readBounded( InputStream body, Duration limit ) throws IOException {
        CompletableFuture< Void > watchdog = CompletableFuture.runAsync( () -> closeQuietly( body ),
                CompletableFuture.delayedExecutor( limit.toNanos(), TimeUnit.NANOSECONDS ) );
        try {
            byte[] contents = body.readNBytes( MAX_RESPONSE_BYTES + 1 );
            if ( watchdog.isDone() ) {
                throw new InterruptedIOException( "GitHub API response was not received in time" );
            }
            if ( contents.length > MAX_RESPONSE_BYTES ) {
                throw new IOException( "GitHub API response exceeds its size limit" );
            }
            return contents;
        } catch ( IOException exception ) {
            if ( watchdog.isDone() ) {
                throw new InterruptedIOException( "GitHub API response was not received in time" );
            }
            throw exception;
        } finally {
            watchdog.cancel( false );
        }
    }

    private static Map< String, Object > parse( byte[] contents ) throws IOException {
        try ( JsonParser parser = JSON.createParser( ObjectReadContext.empty(), contents ) ) {
            if ( parser.nextToken() != JsonToken.START_OBJECT ) {
                throw new IOException( "GitHub API response is not an object" );
            }
            @SuppressWarnings( "unchecked" )
            Map< String, Object > release = ( Map< String, Object > ) readValue( parser );
            return release;
        } catch ( JacksonException exception ) {
            throw new IOException( "GitHub API response is not valid JSON", exception );
        }
    }

    private static Object readValue( JsonParser parser ) {
        return switch ( parser.currentToken() ) {
            case START_OBJECT -> {
                Map< String, Object > object = new HashMap<>();
                for ( JsonToken token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken() ) {
                    String name = parser.currentName();
                    parser.nextToken();
                    object.put( name, readValue( parser ) );
                }
                yield object;
            }
            case START_ARRAY -> {
                List< Object > array = new ArrayList<>();
                for ( JsonToken token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken() ) {
                    array.add( readValue( parser ) );
                }
                yield array;
            }
            case VALUE_STRING -> parser.getString();
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            default -> null;
        };
    }

    private static ReleaseAsset toAsset( Map< String, Object > release ) throws IOException {
        if ( Boolean.TRUE.equals( release.get( "draft" ) ) || Boolean.TRUE.equals( release.get( "prerelease" ) ) ) {
            throw new IOException( "The latest release is a draft or pre-release" );
        }
        Matcher tag = TAG.matcher( text( release, "tag_name" ) );
        ReleaseVersion version = tag.matches() ? ReleaseVersion.parse( tag.group( 1 ) ).orElse( null ) : null;
        if ( version == null ) {
            throw new IOException( "The latest release has an invalid tag" );
        }
        String expectedName = "vDeck-dist-" + version + ".zip";
        Map< String, Object > asset = null;
        if ( release.get( "assets" ) instanceof List< ? > assets ) {
            for ( Object candidate : assets ) {
                if ( candidate instanceof Map< ?, ? > map && expectedName.equals( map.get( "name" ) ) ) {
                    if ( asset != null ) {
                        throw new IOException( "The release has several distribution assets" );
                    }
                    @SuppressWarnings( "unchecked" )
                    Map< String, Object > typed = ( Map< String, Object > ) map;
                    asset = typed;
                }
            }
        }
        if ( asset == null ) {
            throw new IOException( "The release has no asset " + expectedName );
        }
        if ( !( asset.get( "size" ) instanceof Long size ) || size <= 0 || size > MAX_PACKAGE_BYTES ) {
            throw new IOException( "The distribution asset has an invalid size" );
        }
        Matcher digest = DIGEST.matcher( text( asset, "digest" ) );
        if ( !digest.matches() ) {
            throw new IOException( "The distribution asset has no SHA-256 digest" );
        }
        URI download = downloadUri( text( asset, "browser_download_url" ), version, expectedName );
        return new ReleaseAsset( version, expectedName, size, digest.group( 1 ), download );
    }

    private static URI downloadUri( String value, ReleaseVersion version, String name ) throws IOException {
        try {
            URI uri = new URI( value );
            boolean trusted = "https".equals( uri.getScheme() ) && "github.com".equalsIgnoreCase( uri.getHost() )
                    && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null
                    && ( DOWNLOAD_PATH + "v" + version + "/" + name ).equals( uri.getPath() );
            if ( !trusted ) {
                throw new IOException( "The distribution asset has an untrusted download URL" );
            }
            return uri;
        } catch ( URISyntaxException exception ) {
            throw new IOException( "The distribution asset has an invalid download URL", exception );
        }
    }

    private static String text( Map< String, Object > object, String name ) throws IOException {
        if ( object.get( name ) instanceof String value ) {
            return value;
        }
        throw new IOException( "Missing field " + name + " in the GitHub API response" );
    }

    private static Duration positive( Duration duration ) throws InterruptedIOException {
        if ( duration.isZero() || duration.isNegative() ) {
            throw new InterruptedIOException( "Update preparation deadline expired" );
        }
        return duration;
    }

    private static void closeQuietly( InputStream stream ) {
        try {
            stream.close();
        } catch ( IOException exception ) {
            log.debug( "Could not close a timed-out response", exception );
        }
    }
}
