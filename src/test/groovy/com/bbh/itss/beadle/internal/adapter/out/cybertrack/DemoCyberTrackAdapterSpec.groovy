package com.bbh.itss.beadle.internal.adapter.out.cybertrack

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.DemoCyberTrackAdapter.STEP
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static java.time.Clock.fixed
import static java.time.Clock.offset
import static java.time.ZoneOffset.UTC

class DemoCyberTrackAdapterSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final CyberTrackProperties PROPERTIES = new CyberTrackProperties(null, null, 'SCP', 'Task')

    def demo = new DemoCyberTrackAdapter(fixed(NOW, UTC), PROPERTIES)

    def "the demo numbers tickets in its project one after another, without a link, and says it is not connected"() {
        when:
        def first = demo.create(ticket())
        def second = demo.create(ticket())

        then:
        !demo.connected()
        first.key() ==~ /SCP-\d{4}/
        first.url() == null
        (second.key() - 'SCP-') as int == (first.key() - 'SCP-') as int + 1
        demo.read(first.key()) == new CyberTrackTicket(first.key(), 'To Do', null, NOW)
    }

    def "a demo ticket moves on every two minutes on the clock, from To Do to In Progress to Done"() {
        given:
        def key = demo.create(ticket()).key()
        def later = new DemoCyberTrackAdapter(offset(fixed(NOW, UTC), elapsed), PROPERTIES)
        later.created.put(key, NOW)

        expect:
        later.read(key) == new CyberTrackTicket(key, status, null, NOW + STEP.multipliedBy(steps))

        where:
        elapsed                             || status        | steps
        STEP.minusMillis(1)                 || 'To Do'       | 0
        STEP                                || 'In Progress' | 1
        STEP.multipliedBy(2).minusMillis(1) || 'In Progress' | 1
        STEP.multipliedBy(2)                || 'Done'        | 2
        STEP.multipliedBy(100)              || 'Done'        | 2
    }

    def "the demo adopts a ticket of its project it never created as done, and knows no other key"() {
        when:
        def adopted = demo.read('SCP-77')

        then:
        adopted == new CyberTrackTicket('SCP-77', 'Done', null, NOW)
        demo.read('SCP-77') == adopted

        when:
        demo.read('CERT-77')

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'CyberTrack ticket CERT-77 does not exist'
    }

    def "Jira is used only once its URL is set, with SCP and Task by default"() {
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration))
                .withUserConfiguration(Adapters)
                .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBean(CyberTrackPort).class == type
            assert context.getBean(CyberTrackProperties).projectKey() == 'SCP'
            assert context.getBean(CyberTrackProperties).issueType() == 'Task'
        }

        where:
        properties                                           || type
        []                                                   || DemoCyberTrackAdapter
        ['beadle.cybertrack.url=']                           || DemoCyberTrackAdapter
        ['beadle.cybertrack.url=https://cybertrack.bbh.com'] || CyberTrackAdapter
    }

    @EnableConfigurationProperties(CyberTrackProperties)
    @Import([DemoCyberTrackAdapter, CyberTrackAdapter])
    static class Adapters {
    }
}
