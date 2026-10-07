package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now
import static java.lang.Math.abs
import static java.lang.System.currentTimeMillis
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.MICROS

class TimestampsSpec extends Specification {

    def "a timestamp read from a clock keeps microseconds, the precision the database stores"() {
        given:
        def clock = fixed(Instant.parse('2026-10-05T10:15:30.123456789Z'), UTC)

        expect:
        now(clock) == Instant.parse('2026-10-05T10:15:30.123456Z')
    }

    def "the current timestamp has no nanoseconds below a microsecond"() {
        when:
        def now = Timestamps.now()

        then:
        now == now.truncatedTo(MICROS)
        abs(now.toEpochMilli() - currentTimeMillis()) < 60_000
    }
}
