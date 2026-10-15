package com.bbh.itss.beadle.internal.application.cybertrack

import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.domain.callback.PollReport
import com.bbh.itss.beadle.internal.domain.callback.StatusCallback
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.internal.support.Fixtures.UPDATED
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class CyberTrackPollServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:30:00Z')
    static final String UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/

    TrackedTicketPort tracked = Mock()
    CyberTrackPort cyberTrack = Mock()
    CallbackPort callbacks = Mock()
    CyberTrackPort demoCyberTrack = Mock()
    def service = new CyberTrackPollService(tracked, cyberTrack, callbacks, new FollowUp(FollowUp.CLOSED_STATUSES, 3), fixed(NOW, UTC))

    def setup() {
        cyberTrack.connected() >> true
    }

    def cleanup() {
        CallerContext.clear()
    }

    def "nothing is asked of CyberTrack while no ticket is open"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> []
        0 * cyberTrack._
        0 * callbacks._
        report == PollReport.NOTHING
    }

    def "the open tickets are read in one call by the system and a ticket that did not move is only seen"() {
        given:
        Caller reader = null

        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(), trackedTicket(key: 'SCP-1235', correlationId: 'corr-2')]
        1 * cyberTrack.readAll(['SCP-1234', 'SCP-1235']) >> {
            reader = CallerContext.current()
            [cyberTrackTicket(), cyberTrackTicket(key: 'SCP-1235')]
        }
        1 * tracked.save(trackedTicket(lastSeenAt: NOW))
        1 * tracked.save(trackedTicket(key: 'SCP-1235', correlationId: 'corr-2', lastSeenAt: NOW))
        0 * callbacks._
        report == PollReport.NOTHING
        reader.user() == 'system'
        reader.correlationId() ==~ UUID_PATTERN
        CallerContext.current() == Caller.NONE
    }

    def "a ticket that moved is told to Beadle-GUI with the correlation id of its creation and followed from its new status"() {
        given:
        Caller teller = null
        def expected = new StatusCallback(CYBERTRACK, 'SCP-1234', 'In Progress', UPDATED, 'corr-1')

        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket()]
        1 * cyberTrack.readAll(['SCP-1234']) >> [cyberTrackTicket(status: 'In Progress')]

        then:
        1 * callbacks.notify(expected) >> { teller = CallerContext.current() }

        then:
        1 * tracked.save(trackedTicket(status: 'In Progress', lastSeenAt: NOW, lastEventAt: NOW))
        report == new PollReport([expected], [:], [:])
        teller == new Caller('system', 'corr-1')
    }

    def "a ticket that reached #status is told and no longer followed"() {
        when:
        service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(status: 'In Progress')]
        1 * cyberTrack.readAll(['SCP-1234']) >> [cyberTrackTicket(status: status, updated: null)]
        1 * callbacks.notify(new StatusCallback(CYBERTRACK, 'SCP-1234', status, NOW, 'corr-1'))
        1 * tracked.save(trackedTicket(status: status, lastSeenAt: NOW, lastEventAt: NOW, closedAt: NOW))

        where:
        status << ['Done', 'Closed', 'resolved', "Won't Do"]
    }

    def "a ticket whose status was not known yet is told as soon as CyberTrack tells it"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(status: null, lastSeenAt: null)]
        1 * cyberTrack.readAll(['SCP-1234']) >> [cyberTrackTicket()]
        1 * callbacks.notify(new StatusCallback(CYBERTRACK, 'SCP-1234', 'To Do', UPDATED, 'corr-1'))
        1 * tracked.save(trackedTicket(status: 'To Do', lastSeenAt: NOW, lastEventAt: NOW))
        report.sent()*.status() == ['To Do']
    }

    def "a callback Beadle-GUI did not take keeps the former status so that the next poll tells it again"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(), trackedTicket(key: 'SCP-1235')]
        1 * cyberTrack.readAll(_) >> [cyberTrackTicket(status: 'In Progress'), cyberTrackTicket(key: 'SCP-1235', status: 'Done')]
        1 * callbacks.notify({ it.key() == 'SCP-1234' }) >> {
            throw new UncheckedIOException('Beadle-GUI could not take the callback for SCP-1234: I/O error', new IOException())
        }
        1 * callbacks.notify({ it.key() == 'SCP-1235' })
        0 * tracked.save({ it.key() == 'SCP-1234' })
        1 * tracked.save({ it.key() == 'SCP-1235' && it.status() == 'Done' && !it.open() })
        report.sent()*.key() == ['SCP-1235']
        report.failures() == ['SCP-1234': 'Beadle-GUI could not take the callback for SCP-1234: I/O error']
    }

    def "a ticket CyberTrack no longer has, or that cannot be written down, is reported and tried again"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(), trackedTicket(key: 'SCP-1235')]
        1 * cyberTrack.readAll(_) >> [cyberTrackTicket(key: 'SCP-1235')]
        1 * tracked.save(trackedTicket(misses: 1))
        1 * tracked.save({ it.key() == 'SCP-1235' }) >> { throw new IllegalStateException() }
        0 * callbacks._
        report.sent() == []
        report.failures() == ['SCP-1234': 'CyberTrack ticket SCP-1234 does not exist (1 of 3 polls in a row before it is dropped)',
                              'SCP-1235': 'IllegalStateException']
        report.dropped() == [:]
    }

    def "a ticket CyberTrack did not find the last polls in a row is dropped, one found again counts afresh"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(misses: 2), trackedTicket(key: 'SCP-1235', misses: 2)]
        1 * cyberTrack.readAll(['SCP-1234', 'SCP-1235']) >> [cyberTrackTicket(key: 'SCP-1235')]
        1 * tracked.save(trackedTicket(misses: 3, closedAt: NOW))
        1 * tracked.save(trackedTicket(key: 'SCP-1235', lastSeenAt: NOW, misses: 0))
        0 * callbacks._
        report == new PollReport([], [:], ['SCP-1234': 'CyberTrack ticket SCP-1234 does not exist: CyberTrack did not find it 3 polls in a row'])
    }

    def "a ticket that reached a closed status of its own workflow is no longer followed"() {
        given:
        def service = new CyberTrackPollService(tracked, cyberTrack, callbacks, new FollowUp(['Remediated'], 3), fixed(NOW, UTC))

        when:
        service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(), trackedTicket(key: 'SCP-1235')]
        1 * cyberTrack.readAll(_) >> [cyberTrackTicket(status: 'Remediated'), cyberTrackTicket(key: 'SCP-1235', status: 'Done')]
        1 * tracked.save(trackedTicket(status: 'Remediated', lastSeenAt: NOW, lastEventAt: NOW, closedAt: NOW))
        1 * tracked.save(trackedTicket(key: 'SCP-1235', status: 'Done', lastSeenAt: NOW, lastEventAt: NOW))
    }

    def "a CyberTrack that cannot be read fails the poll without telling anything"() {
        when:
        service.poll()

        then:
        1 * tracked.open() >> [trackedTicket()]
        1 * cyberTrack.readAll(_) >> { throw new UncheckedIOException('CyberTrack could not read the tickets SCP-1234: 503', new IOException()) }
        0 * callbacks._
        0 * tracked.save(_)
        thrown(UncheckedIOException)
    }

    def "a ticket made up by the demo is dropped once CyberTrack is configured, and a real one while the demo plays"() {
        given:
        def demo = new CyberTrackPollService(tracked, demoCyberTrack, callbacks, FollowUp.DEFAULT, fixed(NOW, UTC))

        when:
        def live = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(key: 'SCP-1', demo: true), trackedTicket(key: 'SCP-2')]
        1 * tracked.save(trackedTicket(key: 'SCP-1', demo: true, closedAt: NOW))
        1 * cyberTrack.readAll(['SCP-2']) >> [cyberTrackTicket(key: 'SCP-2')]
        1 * tracked.save(trackedTicket(key: 'SCP-2', lastSeenAt: NOW))
        live == new PollReport([], [:], ['SCP-1': CyberTrackPollService.DEMO_TICKET])

        when:
        def played = demo.poll()

        then:
        1 * tracked.open() >> [trackedTicket(key: 'SCP-1', demo: true), trackedTicket(key: 'SCP-2')]
        1 * tracked.save(trackedTicket(key: 'SCP-2', closedAt: NOW))
        1 * demoCyberTrack.readAll(['SCP-1']) >> [cyberTrackTicket(key: 'SCP-1')]
        1 * tracked.save(trackedTicket(key: 'SCP-1', demo: true, lastSeenAt: NOW))
        played == new PollReport([], [:], ['SCP-2': CyberTrackPollService.REAL_TICKET])
    }

    def "only dropped tickets ask nothing of CyberTrack, and a drop that cannot be written down is tried again"() {
        when:
        def report = service.poll()

        then:
        1 * tracked.open() >> [trackedTicket(key: 'SCP-1', demo: true), trackedTicket(key: 'SCP-2', demo: true)]
        1 * tracked.save({ it.key() == 'SCP-1' }) >> { throw new IllegalStateException('database down') }
        1 * tracked.save(trackedTicket(key: 'SCP-2', demo: true, closedAt: NOW))
        0 * cyberTrack.readAll(_)
        report == new PollReport([], ['SCP-1': 'database down'], ['SCP-2': CyberTrackPollService.DEMO_TICKET])
    }
}
