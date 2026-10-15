package com.bbh.itss.beadle.internal.application.cybertrack

import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
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

    def "a secure coding ticket is created in #system as it is and followed from the status it was created in"() {
        given:
        def created = new CreatedTicket('SCP-1234', null)
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))
        cyberTrack.connected() >> connected

        when:
        def result = service.create(ticket())

        then:
        1 * cyberTrack.create(ticket()) >> created

        then:
        1 * cyberTrack.read('SCP-1234') >> cyberTrackTicket()
        1 * tracked.track(new TrackedTicket('SCP-1234', 'To Do', 'corr-1', NOW, NOW, null, null, !connected))
        result == created

        where:
        system                    | connected
        'CyberTrack'              | true
        'the demo of CyberTrack'  | false
    }

    def "a ticket whose status cannot be read after its creation is followed without a status"() {
        given:
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))

        when:
        def result = service.create(ticket())

        then:
        1 * cyberTrack.create(ticket()) >> new CreatedTicket('SCP-1234', null)
        1 * cyberTrack.read('SCP-1234') >> { throw failure }
        1 * tracked.track(new TrackedTicket('SCP-1234', null, 'corr-1', NOW, null, null, null, true))
        result.key() == 'SCP-1234'

        where:
        failure << [new UncheckedIOException('CyberTrack could not read the ticket SCP-1234: 503', new IOException()),
                    new NoSuchElementException('CyberTrack ticket SCP-1234 does not exist')]
    }

    def "a ticket CyberTrack refused to create is not followed"() {
        when:
        service.create(ticket())

        then:
        1 * cyberTrack.create(ticket()) >> { throw new UncheckedIOException('CyberTrack could not create: 400', new IOException()) }
        thrown(UncheckedIOException)
        0 * tracked._
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
