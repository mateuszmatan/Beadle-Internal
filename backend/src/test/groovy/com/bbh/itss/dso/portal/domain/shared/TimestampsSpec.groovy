package com.bbh.itss.dso.portal.domain.shared

import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class TimestampsSpec extends Specification {

    def "a timestamp read from a clock keeps microseconds, the precision the database stores"() {
        given:
        def clock = Clock.fixed(Instant.parse('2026-10-05T10:15:30.123456789Z'), ZoneOffset.UTC)

        expect:
        Timestamps.now(clock) == Instant.parse('2026-10-05T10:15:30.123456Z')
    }

    def "the current timestamp has no nanoseconds below a microsecond"() {
        when:
        def now = Timestamps.now()

        then:
        now == now.truncatedTo(ChronoUnit.MICROS)
        Math.abs(now.toEpochMilli() - System.currentTimeMillis()) < 60_000
    }
}
