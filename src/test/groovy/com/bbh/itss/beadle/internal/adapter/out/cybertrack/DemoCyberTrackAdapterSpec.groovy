package com.bbh.itss.beadle.internal.adapter.out.cybertrack

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.DemoCyberTrackAdapter.STEP
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static java.time.Clock.fixed
import static java.time.Clock.offset
import static java.time.ZoneOffset.UTC

class DemoCyberTrackAdapterSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final CyberTrackProperties PROPERTIES = new CyberTrackProperties(null, null, 'SCP', 'Task')

    TrackedTicketPort tracked = Stub()
    def demo = new DemoCyberTrackAdapter(fixed(NOW, UTC), PROPERTIES, tracked)

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
        def later = new DemoCyberTrackAdapter(offset(fixed(NOW, UTC), elapsed), PROPERTIES, tracked)
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

    def "the demo reads several tickets at once, on its clock, leaving out the keys it does not know"() {
        given:
        def key = demo.create(ticket()).key()
        def later = new DemoCyberTrackAdapter(offset(fixed(NOW, UTC), STEP), PROPERTIES, tracked)
        later.created.put(key, NOW)

        expect:
        later.readAll([key, 'CERT-77', 'SCP-77']) == [new CyberTrackTicket(key, 'In Progress', null, NOW + STEP),
                                                      new CyberTrackTicket('SCP-77', 'Done', null, NOW + STEP)]
        later.readAll([]) == []
    }

    def "after a restart, a followed ticket moves on from the creation time its row keeps"() {
        given:
        tracked.find('SCP-3502') >> Optional.of(trackedTicket(key: 'SCP-3502', createdAt: NOW - Duration.ofMinutes(1)))
        def restarted = new DemoCyberTrackAdapter(fixed(NOW, UTC), PROPERTIES, tracked)
        def later = new DemoCyberTrackAdapter(offset(fixed(NOW, UTC), STEP), PROPERTIES, tracked)

        expect:
        restarted.read('SCP-3502') == new CyberTrackTicket('SCP-3502', 'To Do', null, NOW - Duration.ofMinutes(1))
        later.read('SCP-3502') == new CyberTrackTicket('SCP-3502', 'In Progress', null, NOW + Duration.ofMinutes(1))
        restarted.readAll(['SCP-3502'])*.status() == ['To Do']
    }

    def "after a restart, the keys go on after the highest one followed"() {
        given:
        tracked.highestKey('SCP-') >> Optional.of('SCP-4321')

        expect:
        demo.create(ticket()).key() == 'SCP-4322'
        demo.create(ticket()).key() == 'SCP-4323'
    }

    def "the keys start at random when no key is followed or the highest cannot be found"() {
        given:
        tracked.highestKey('SCP-') >> highest

        expect:
        demo.create(ticket()).key() ==~ /SCP-\d{4}/

        where:
        highest << [Optional.empty(), Optional.of('SCP-'), { throw new IllegalStateException('database down') }]
    }

    def "the live CyberTrack adapter is used only once its URL is set, with SCP and Task by default"() {
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration))
                .withUserConfiguration(Adapters)
                .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)
                .withBean(TrackedTicketPort, { tracked } as Supplier<TrackedTicketPort>)

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
