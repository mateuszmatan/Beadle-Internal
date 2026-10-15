package com.bbh.itss.beadle.internal.application.calllog

import com.bbh.itss.beadle.internal.domain.calllog.Caller
import spock.lang.Specification

import java.util.concurrent.Executors

class CallerContextSpec extends Specification {

    def cleanup() {
        CallerContext.clear()
    }

    def "the caller is held per thread until cleared, and nobody by default"() {
        given:
        def caller = new Caller('Mateusz Matan', 'corr-1')

        expect:
        CallerContext.current() == Caller.NONE

        when:
        CallerContext.set(caller)

        then:
        CallerContext.current() == caller
        Executors.newSingleThreadExecutor().submit { CallerContext.current() }.get() == Caller.NONE

        when:
        CallerContext.clear()

        then:
        CallerContext.current() == Caller.NONE
    }

    def "work can run as another caller and the previous one comes back afterwards"() {
        given:
        def before = new Caller('Mateusz Matan', 'corr-1')
        def system = new Caller('system', 'tick-1')
        CallerContext.set(before)

        when:
        def seen = CallerContext.as(system) { CallerContext.current() }

        then:
        seen == system
        CallerContext.current() == before

        when:
        CallerContext.clear()
        CallerContext.as(system) { CallerContext.current() }

        then:
        CallerContext.current() == Caller.NONE
    }
}
