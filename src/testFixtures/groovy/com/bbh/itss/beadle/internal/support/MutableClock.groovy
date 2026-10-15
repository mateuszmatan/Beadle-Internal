package com.bbh.itss.beadle.internal.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

import static java.time.ZoneOffset.UTC

/**
 * A clock that stands still until a spec moves it, so that the demo adapters change only when the spec says so.
 */
class MutableClock extends Clock {

    private volatile Instant now
    private final ZoneId zone

    MutableClock(Instant start, ZoneId zone = UTC) {
        this.now = start
        this.zone = zone
    }

    @Override
    ZoneId getZone() {
        zone
    }

    @Override
    Clock withZone(ZoneId other) {
        new MutableClock(now, other)
    }

    @Override
    Instant instant() {
        now
    }

    synchronized Instant advance(Duration duration) {
        now = now + duration
    }
}
