package com.bbh.itss.beadle.internal.domain.cybertrack

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.support.Fixtures.CREATED
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket

class TrackedTicketSpec extends Specification {

    static final Instant LATER = Instant.parse('2026-10-15T09:30:00Z')

    def "a created ticket is followed from the status it was created in, seen then if CyberTrack told it"() {
        expect:
        TrackedTicket.created('SCP-1234', 'To Do', 'corr-1', CREATED) == trackedTicket()
        TrackedTicket.created('SCP-1234', null, 'corr-1', CREATED) == trackedTicket(status: null, lastSeenAt: null)
        TrackedTicket.created('SCP-1234', 'To Do', 'corr-1', CREATED).open()
        !TrackedTicket.created('SCP-1234', 'Done', 'corr-1', CREATED).open()
    }

    def "a ticket moved when CyberTrack tells another status than the last one Beadle-GUI knows"() {
        expect:
        trackedTicket(status: known).movedIn(cyberTrackTicket(status: current)) == moved

        where:
        known         | current       || moved
        'To Do'       | 'To Do'       || false
        'To Do'       | 'In Progress' || true
        null          | 'To Do'       || true
        'To Do'       | null          || false
        null          | null          || false
    }

    def "a reported status is the new known status, closing the ticket when it is a final one"() {
        when:
        def reported = trackedTicket().reported(status, LATER)

        then:
        reported == trackedTicket(status: status, lastSeenAt: LATER, lastEventAt: LATER, closedAt: closed ? LATER : null)
        reported.open() == !closed

        where:
        status        || closed
        'In Progress' || false
        'Done'        || true
        ' closed '    || true
        'Resolved'    || true
        'Cancelled'   || true
        'Rejected'    || true
        "Won't Do"    || true
        'Reopened'    || false
    }

    def "no status is not a closed one"() {
        expect:
        !TrackedTicket.closed(null)
    }
}
