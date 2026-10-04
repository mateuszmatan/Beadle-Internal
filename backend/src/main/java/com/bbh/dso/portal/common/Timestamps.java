package com.bbh.dso.portal.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps at the precision the database keeps, microseconds, so a value returned right after saving is the
 * one later reads return.
 */
public final class Timestamps {

    private Timestamps() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
