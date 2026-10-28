package com.github.pplociennik.vdeck.shared;

import com.github.pplociennik.vdeck.shared.service.SystemPropertiesService;
import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.Optional;

import static com.github.pplociennik.vdeck.shared.PropertyReader.readBoolean;
import static com.github.pplociennik.vdeck.shared.PropertyReader.readInt;
import static java.util.Objects.requireNonNull;

/**
 * Interprets a pair of log retention settings of {@code vDeck.properties}: a switch and a number of days.
 * The game server logs (AD-016) and the panel run logs (AD-032) use the same rule, so both read it here.
 *
 * <pre>
 * switch false ---------------------------&gt; keep every log
 * days 0 ---------------------------------&gt; keep every log
 * days N &gt; 0 -----------------------------&gt; keep today and the N days before it
 * days negative or not a number ----------&gt; the setting's default, logged at WARN
 * </pre>
 *
 * <p>The class is static and stateless; it reads the loaded settings and never writes them.
 */
@Log4j2
public final class LogRetention {

    private LogRetention() {
    }

    /**
     * Returns the oldest day whose logs are kept, or nothing when every log is kept.
     *
     * @param service
     *         service holding the loaded {@code vDeck.properties}
     * @param enabled
     *         switch turning the retention on or off
     * @param days
     *         number of days before today whose logs are kept, {@code 0} keeping every log
     * @param today
     *         current local date
     * @return oldest kept day, or an empty result when retention is off or keeps every log
     * @throws NullPointerException
     *         if an argument is null
     */
    public static @Nonnull Optional< LocalDate > oldestKeptDay( @Nonnull SystemPropertiesService service,
                                                                @Nonnull VdeckProperty enabled,
                                                                @Nonnull VdeckProperty days,
                                                                @Nonnull LocalDate today ) {
        requireNonNull( service, "service must not be null" );
        requireNonNull( enabled, "enabled must not be null" );
        requireNonNull( days, "days must not be null" );
        requireNonNull( today, "today must not be null" );

        if ( !readBoolean( service, VdeckProperty.FILE_NAME, enabled, enabled.getDefaultValue() ) ) {
            log.debug( "Retention switched off by {}; keeping every log", enabled.getName() );
            return Optional.empty();
        }

        int keptDays = retentionDays( service, days );
        if ( keptDays == 0 ) {
            log.debug( "Retention of 0 days set by {}; keeping every log", days.getName() );
            return Optional.empty();
        }
        return Optional.of( today.minusDays( keptDays ) );
    }

    /**
     * Reads the number of days. A negative number would remove today's logs, so it is replaced by the
     * default, as is a value that is not a number.
     */
    private static int retentionDays( SystemPropertiesService service, VdeckProperty days ) {
        int keptDays = readInt( service, VdeckProperty.FILE_NAME, days, days.getDefaultValue() );
        if ( keptDays < 0 ) {
            log.warn( "Setting '{}' is negative; using its default", days.getName() );
            return Integer.parseInt( days.getDefaultValue() );
        }
        return keptDays;
    }
}
