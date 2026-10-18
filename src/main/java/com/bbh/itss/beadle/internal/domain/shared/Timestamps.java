package com.bbh.itss.beadle.internal.domain.shared;

import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.MICROS;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Timestamps {

    /**
     * Now to the microsecond, as the database keeps it.
     */
    public static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(MICROS);
    }
}
