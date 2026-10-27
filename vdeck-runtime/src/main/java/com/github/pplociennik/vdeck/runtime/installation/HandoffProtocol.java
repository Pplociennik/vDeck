package com.github.pplociennik.vdeck.runtime.installation;

import com.github.pplociennik.vdeck.runtime.api.ProcessIdentity;
import com.github.pplociennik.vdeck.runtime.snapshot.FileWorldSnapshotStore;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Objects.requireNonNull;

/**
 * Passes the installation lock from a running process to a successor it started, with confirmation
 * (AD-024). A bare PID or command-line flag never authorizes a takeover.
 *
 * <pre>
 * predecessor (holds gate + lock)          successor (started in waiting mode)
 *   start successor --handoff &lt;id&gt;
 *   publish &lt;id&gt;.handoff: role, identities ---> wait for the descriptor, verify own and parent identity
 *   release the installation lock only       ---> take the installation lock (never the gate)
 *   wait for &lt;id&gt;.ack, verify identity    &lt;--- publish &lt;id&gt;.ack with own identity
 *   exit (gate freed by the OS) or keep the gate while waiting for the panel
 * </pre>
 *
 * <p>Descriptors live in {@code .vDeck/handoff}, are bounded, written durably, used once and deleted by
 * the predecessor after a valid acknowledgement. Every wait is bounded by a monotonic timeout; a timeout
 * grants no right to change the installation.
 */
@Log4j2
public final class HandoffProtocol {

    /**
     * Default bound of every handoff wait.
     */
    public static final Duration TIMEOUT = Duration.ofSeconds( 10 );

    private static final int MAX_DESCRIPTOR_BYTES = 4 * 1024;

    private static final String DESCRIPTOR_SUFFIX = ".handoff";

    private static final String ACKNOWLEDGEMENT_SUFFIX = ".ack";

    private final UpdateLayout layout;

    private final Duration pollInterval;

    private final ProcessHandle self;

    private final Optional< ProcessHandle > parent;

    /**
     * Creates the protocol for the current process.
     *
     * @param installation
     *         canonical absolute installation root
     * @throws NullPointerException
     *         if the installation is null
     * @throws IllegalArgumentException
     *         if the installation is relative
     */
    public HandoffProtocol( @Nonnull Path installation ) {
        this( installation, Duration.ofMillis( 100 ), ProcessHandle.current(), ProcessHandle.current().parent() );
    }

    /**
     * Creates the protocol with explicit process boundaries.
     *
     * @param installation
     *         canonical absolute installation root
     * @param pollInterval
     *         pause between checks of a wait
     * @param self
     *         process acting as predecessor or successor
     * @param parent
     *         operating-system parent of {@code self}, if still known
     */
    HandoffProtocol( @Nonnull Path installation, @Nonnull Duration pollInterval, @Nonnull ProcessHandle self,
                     @Nonnull Optional< ProcessHandle > parent ) {
        this.layout = new UpdateLayout( requireNonNull( installation, "installation must not be null" ) );
        this.pollInterval = requireNonNull( pollInterval, "pollInterval must not be null" );
        this.self = requireNonNull( self, "self must not be null" );
        this.parent = requireNonNull( parent, "parent must not be null" );
    }

    /**
     * Creates a new operation identifier passed to the successor as {@code --handoff <id>}.
     *
     * @return random identifier safe to use as a file name
     */
    public static @Nonnull String newOperationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Publishes the one-time descriptor naming the started successor. Must be called while this process
     * still holds both locks; only afterwards may it release the installation lock.
     *
     * @param operationId
     *         identifier passed to the successor
     * @param role
     *         role the successor must declare
     * @param successor
     *         started successor process
     * @return verified identity of the successor recorded in the descriptor
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the identifier is malformed
     * @throws UncheckedIOException
     *         if the descriptor cannot be written or the successor identity cannot be captured
     */
    public @Nonnull ProcessIdentity publish( @Nonnull String operationId, @Nonnull HandoffRole role,
                                            @Nonnull ProcessHandle successor ) {
        requireNonNull( role, "role must not be null" );
        requireNonNull( successor, "successor must not be null" );
        String id = validOperationId( operationId );
        ProcessIdentity successorIdentity = identityOf( successor );
        Properties descriptor = new Properties();
        descriptor.setProperty( "operation", id );
        descriptor.setProperty( "role", role.name() );
        writeIdentity( descriptor, "parent", identityOf( self ) );
        writeIdentity( descriptor, "child", successorIdentity );
        try {
            Path directory = prepareDirectory();
            DurableFiles.writeProperties( directory.resolve( id + DESCRIPTOR_SUFFIX ), descriptor, "vDeck handoff" );
        } catch ( IOException exception ) {
            throw new UncheckedIOException( "Could not publish the handoff descriptor", exception );
        }
        log.info( "Published handoff {} for role {} to process {}", id, role, successorIdentity.pid() );
        return successorIdentity;
    }

    /**
     * Waits until the successor acknowledges that it holds the installation lock. Deletes the descriptor
     * and acknowledgement afterwards, whatever the result.
     *
     * @param operationId
     *         identifier of the published handoff
     * @param successor
     *         identity recorded by {@link #publish}
     * @param timeout
     *         monotonic bound of the wait
     * @return {@code true} when a valid acknowledgement arrived from the still running successor
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the identifier is malformed
     */
    public boolean awaitAcknowledgement( @Nonnull String operationId, @Nonnull ProcessIdentity successor,
                                         @Nonnull Duration timeout ) {
        requireNonNull( successor, "successor must not be null" );
        requireNonNull( timeout, "timeout must not be null" );
        String id = validOperationId( operationId );
        Path acknowledgement = layout.handoffDirectory().resolve( id + ACKNOWLEDGEMENT_SUFFIX );
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while ( System.nanoTime() - deadline < 0 ) {
                if ( Files.exists( acknowledgement, NOFOLLOW_LINKS ) ) {
                    boolean valid = isValidAcknowledgement( acknowledgement, id, successor );
                    log.info( "Handoff {} acknowledged: {}", id, valid );
                    return valid;
                }
                if ( !isAlive( successor ) ) {
                    log.warn( "Successor of handoff {} exited before acknowledging", id );
                    return false;
                }
                pause();
            }
            log.warn( "Handoff {} was not acknowledged within {}", id, timeout );
            return false;
        } finally {
            deleteQuietly( layout.handoffDirectory().resolve( id + DESCRIPTOR_SUFFIX ) );
            deleteQuietly( acknowledgement );
        }
    }

    /**
     * Accepts a handoff as the successor: waits for the descriptor, verifies the declared role, its own
     * identity and its parent's identity, takes the installation lock released by the predecessor and
     * acknowledges. Never touches the startup gate, which the predecessor still holds.
     *
     * @param operationId
     *         identifier received as {@code --handoff <id>}
     * @param role
     *         role this process plays
     * @param timeout
     *         monotonic bound of the whole acceptance
     * @return lock taken over and the verified predecessor
     * @throws NullPointerException
     *         if an argument is null
     * @throws IllegalArgumentException
     *         if the identifier is malformed
     * @throws IllegalStateException
     *         if the descriptor is missing, foreign or invalid, or the lock is not released in time
     */
    public @Nonnull AcceptedHandoff accept( @Nonnull String operationId, @Nonnull HandoffRole role,
                                            @Nonnull Duration timeout ) {
        requireNonNull( role, "role must not be null" );
        requireNonNull( timeout, "timeout must not be null" );
        String id = validOperationId( operationId );
        long deadline = System.nanoTime() + timeout.toNanos();
        Path descriptorFile = layout.handoffDirectory().resolve( id + DESCRIPTOR_SUFFIX );
        awaitFile( descriptorFile, deadline );
        ProcessIdentity predecessor = verifyDescriptor( descriptorFile, id, role );
        InstallationLock lock = awaitLock( deadline );
        try {
            Properties acknowledgement = new Properties();
            acknowledgement.setProperty( "operation", id );
            writeIdentity( acknowledgement, "child", identityOf( self ) );
            DurableFiles.writeProperties( layout.handoffDirectory().resolve( id + ACKNOWLEDGEMENT_SUFFIX ),
                    acknowledgement, "vDeck handoff acknowledgement" );
        } catch ( IOException | RuntimeException exception ) {
            lock.close();
            throw new IllegalStateException( "Could not acknowledge handoff " + id, exception );
        }
        log.info( "Accepted handoff {} as {} from process {}", id, role, predecessor.pid() );
        return new AcceptedHandoff( lock, predecessor );
    }

    /**
     * Waits until a process with the given identity no longer runs. A reused PID with another creation
     * time counts as exited.
     *
     * @param process
     *         identity of the awaited process
     * @param timeout
     *         bound of the wait
     * @return {@code true} once the process has exited, {@code false} when it still runs at the timeout
     * @throws NullPointerException
     *         if an argument is null
     */
    public static boolean awaitExit( @Nonnull ProcessIdentity process, @Nonnull Duration timeout ) {
        requireNonNull( process, "process must not be null" );
        requireNonNull( timeout, "timeout must not be null" );
        Optional< ProcessHandle > handle = ProcessHandle.of( process.pid() );
        if ( handle.isEmpty() || !isSameProcess( handle.get(), process ) ) {
            return true;
        }
        try {
            handle.get().onExit().get( timeout.toNanos(), TimeUnit.NANOSECONDS );
            return true;
        } catch ( TimeoutException exception ) {
            return false;
        } catch ( ExecutionException exception ) {
            throw new IllegalStateException( "Could not observe the exit of process " + process.pid(), exception );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Deletes descriptors and acknowledgements left by interrupted handoffs. Only the holder of both the
     * startup gate and the installation lock may call it, because no handoff can then be in progress.
     */
    public void discardStale() {
        Path directory = layout.handoffDirectory();
        if ( !Files.isDirectory( directory, NOFOLLOW_LINKS ) ) {
            return;
        }
        try {
            FileWorldSnapshotStore.checkDirectory( layout.installation(), directory );
            try ( DirectoryStream< Path > files = Files.newDirectoryStream( directory ) ) {
                for ( Path file : files ) {
                    if ( Files.isRegularFile( file, NOFOLLOW_LINKS ) ) {
                        Files.delete( file );
                        log.debug( "Deleted stale handoff file {}", file.getFileName() );
                    }
                }
            }
        } catch ( IOException exception ) {
            log.warn( "Could not delete stale handoff files", exception );
        }
    }

    private Path prepareDirectory() throws IOException {
        Path directory = layout.handoffDirectory();
        FileWorldSnapshotStore.checkDirectory( layout.installation(), layout.installation() );
        Files.createDirectories( directory );
        FileWorldSnapshotStore.checkDirectory( layout.installation(), directory );
        return directory;
    }

    private void awaitFile( Path file, long deadline ) {
        while ( !Files.exists( file, NOFOLLOW_LINKS ) ) {
            if ( System.nanoTime() - deadline >= 0 ) {
                throw new IllegalStateException( "Handoff descriptor did not appear in time" );
            }
            pause();
        }
    }

    private ProcessIdentity verifyDescriptor( Path file, String id, HandoffRole role ) {
        try {
            FileWorldSnapshotStore.checkDirectory( layout.installation(), file.getParent() );
            Properties descriptor = DurableFiles.readProperties( file, MAX_DESCRIPTOR_BYTES );
            if ( !id.equals( descriptor.getProperty( "operation" ) )
                    || !role.name().equals( descriptor.getProperty( "role" ) ) ) {
                throw new IllegalStateException( "Handoff descriptor names another operation or role" );
            }
            if ( !readIdentity( descriptor, "child" ).equals( identityOf( self ) ) ) {
                throw new IllegalStateException( "Handoff descriptor names another successor" );
            }
            ProcessIdentity predecessor = readIdentity( descriptor, "parent" );
            ProcessHandle actualParent = parent.orElseThrow(
                    () -> new IllegalStateException( "The predecessor no longer runs" ) );
            if ( !identityOf( actualParent ).equals( predecessor ) ) {
                throw new IllegalStateException( "Handoff descriptor names another predecessor" );
            }
            return predecessor;
        } catch ( IOException exception ) {
            throw new IllegalStateException( "Unreadable handoff descriptor", exception );
        }
    }

    private InstallationLock awaitLock( long deadline ) {
        while ( true ) {
            Optional< InstallationLock > lock = InstallationLock.tryAcquire( layout.installation(),
                    InstallationLock.Kind.INSTALLATION );
            if ( lock.isPresent() ) {
                return lock.get();
            }
            if ( System.nanoTime() - deadline >= 0 ) {
                throw new IllegalStateException( "The predecessor did not release the installation lock in time" );
            }
            pause();
        }
    }

    private boolean isValidAcknowledgement( Path file, String id, ProcessIdentity successor ) {
        try {
            Properties acknowledgement = DurableFiles.readProperties( file, MAX_DESCRIPTOR_BYTES );
            return id.equals( acknowledgement.getProperty( "operation" ) )
                    && readIdentity( acknowledgement, "child" ).equals( successor ) && isAlive( successor );
        } catch ( IOException | RuntimeException exception ) {
            log.warn( "Rejected invalid handoff acknowledgement {}", id, exception );
            return false;
        }
    }

    private static boolean isAlive( ProcessIdentity identity ) {
        return ProcessHandle.of( identity.pid() ).filter( ProcessHandle::isAlive )
                .filter( handle -> isSameProcess( handle, identity ) ).isPresent();
    }

    private static boolean isSameProcess( ProcessHandle handle, ProcessIdentity identity ) {
        return handle.info().startInstant().map( identity.startedAt()::equals ).orElse( false );
    }

    private static ProcessIdentity identityOf( ProcessHandle handle ) {
        String command = handle.info().command().orElseThrow(
                () -> new IllegalStateException( "Process executable is unavailable" ) );
        return ProcessIdentity.capture( handle, Path.of( command ) );
    }

    private static void writeIdentity( Properties values, String prefix, ProcessIdentity identity ) {
        values.setProperty( prefix + ".pid", Long.toString( identity.pid() ) );
        values.setProperty( prefix + ".started", identity.startedAt().toString() );
        values.setProperty( prefix + ".executable", identity.executable().toString() );
    }

    private static ProcessIdentity readIdentity( Properties values, String prefix ) {
        String pid = requireNonNull( values.getProperty( prefix + ".pid" ), "Missing " + prefix + " PID" );
        String started = requireNonNull( values.getProperty( prefix + ".started" ), "Missing " + prefix + " start" );
        String executable = requireNonNull( values.getProperty( prefix + ".executable" ),
                "Missing " + prefix + " executable" );
        return new ProcessIdentity( Long.parseLong( pid ), Instant.parse( started ), Path.of( executable ) );
    }

    private static String validOperationId( String operationId ) {
        requireNonNull( operationId, "operationId must not be null" );
        if ( !UUID.fromString( operationId ).toString().equals( operationId ) ) {
            throw new IllegalArgumentException( "Malformed handoff identifier" );
        }
        return operationId;
    }

    private void pause() {
        try {
            Thread.sleep( pollInterval );
        } catch ( InterruptedException exception ) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException( "Interrupted while waiting for a handoff", exception );
        }
    }

    private static void deleteQuietly( Path file ) {
        try {
            Files.deleteIfExists( file );
        } catch ( IOException exception ) {
            log.warn( "Could not delete handoff file {}", file.getFileName(), exception );
        }
    }
}
