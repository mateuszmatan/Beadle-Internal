package com.bbh.itss.dso.portal.domain.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Timestamps {

    private Timestamps() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
