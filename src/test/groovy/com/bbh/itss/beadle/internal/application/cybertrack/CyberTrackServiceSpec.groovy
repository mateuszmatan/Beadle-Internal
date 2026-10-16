package com.bbh.itss.beadle.internal.application.cybertrack

import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class CyberTrackServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:30:00Z')

    CyberTrackPort cyberTrack = Mock()
    TrackedTicketPort tracked = Mock()
    def service = new CyberTrackService(cyberTrack, tracked, fixed(NOW, UTC))

    def cleanup() {
        CallerContext.clear()
    }

    def "a secure coding ticket is created in #system as it is, answered at once and followed without a status"() {
        given:
        def created = new CreatedTicket('SCP-1234', url)
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))
        cyberTrack.connected() >> connected

        when:
        def result = service.create(ticket())

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.empty()

        then:
        1 * cyberTrack.create(ticket()) >> created

        then:
        1 * tracked.track(new TrackedTicket('SCP-1234', null, 'corr-1', NOW, null, null, null, !connected, 0, 'CHG0031001', url))
        0 * cyberTrack.read(_)
        result == created

        where:
        system                   | connected | url
        'CyberTrack'             | true      | 'https://cybertrack.bbh.com/browse/SCP-1234'
        'the demo of CyberTrack' | false     | null
    }

    def "a change whose ticket is still followed gets that ticket again instead of a second one"() {
        given:
        cyberTrack.connected() >> true

        when:
        def result = service.create(ticket())

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.of(trackedTicket(status: 'In Progress'))
        0 * cyberTrack.create(_)
        0 * tracked.track(_)
        result == new CreatedTicket('SCP-1234', 'https://cybertrack.bbh.com/browse/SCP-1234')
    }

    def "a ticket the demo raised for the change is not handed out once CyberTrack is configured, nor the other way round"() {
        given:
        cyberTrack.connected() >> connected

        when:
        def result = service.create(ticket())

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.of(trackedTicket(demo: connected))
        1 * cyberTrack.create(ticket()) >> new CreatedTicket('SCP-2000', null)
        1 * tracked.track({ it.key() == 'SCP-2000' && it.demo() == !connected })
        result.key() == 'SCP-2000'

        where:
        connected << [true, false]
    }

    def "two requests for the same change at once raise one ticket"() {
        given:
        Map<String, TrackedTicket> followed = [:].asSynchronized()
        def memory = [openFor: { String change -> Optional.ofNullable(followed.values().find { it.changeNumber() == change }) },
                      track  : { TrackedTicket ticket -> followed[ticket.key()] = ticket }] as TrackedTicketPort
        int created = 0
        def slow = [connected: { true },
                    create   : { SecureCodingTicket ticket ->
                        Thread.sleep(200)
                        new CreatedTicket('SCP-' + (1234 + created++), null)
                    }] as CyberTrackPort
        def service = new CyberTrackService(slow, memory, fixed(NOW, UTC))
        def pool = Executors.newFixedThreadPool(2)

        when:
        def answers = [pool.submit({ service.create(ticket()) } as Callable),
                       pool.submit({ service.create(ticket()) } as Callable)]*.get()

        then:
        created == 1
        answers*.key() == ['SCP-1234', 'SCP-1234']

        cleanup:
        pool.shutdown()
    }

    def "a ticket CyberTrack refused to create is not followed"() {
        when:
        service.create(ticket())

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.empty()
        1 * cyberTrack.create(ticket()) >> { throw new UncheckedIOException('CyberTrack could not create: 400', new IOException()) }
        thrown(UncheckedIOException)
        0 * tracked.track(_)
    }

    def "a ticket is read by its trimmed key"() {
        given:
        def found = cyberTrackTicket()

        when:
        def result = service.read(' SCP-1234 ')

        then:
        1 * cyberTrack.read('SCP-1234') >> found
        result == found
    }

    def "a ticket without a key is not found without asking CyberTrack"() {
        when:
        service.read(key)

        then:
        thrown(NoSuchElementException)
        0 * cyberTrack._

        where:
        key << [null, '', '  ']
    }
}
