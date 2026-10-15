package com.bbh.itss.beadle.internal.domain.cybertrack

import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp.DEFAULT
import static com.bbh.itss.beadle.internal.support.Fixtures.CREATED
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket

class TrackedTicketSpec extends Specification {

    static final Instant LATER = Instant.parse('2026-10-15T09:30:00Z')

    def "a created ticket is followed from the status it was created in, seen then if CyberTrack told it"() {
        expect:
        TrackedTicket.created('SCP-1234', 'To Do', 'corr-1', CREATED, false, DEFAULT) == trackedTicket()
        TrackedTicket.created('SCP-1234', null, 'corr-1', CREATED, false, DEFAULT) == trackedTicket(status: null, lastSeenAt: null)
        TrackedTicket.created('SCP-1234', 'To Do', 'corr-1', CREATED, true, DEFAULT) == trackedTicket(demo: true)
        TrackedTicket.created('SCP-1234', 'To Do', 'corr-1', CREATED, false, DEFAULT).open()
        !TrackedTicket.created('SCP-1234', 'Done', 'corr-1', CREATED, false, DEFAULT).open()
        TrackedTicket.created('SCP-1234', 'Remediated', 'corr-1', CREATED, false, new FollowUp(['Remediated'], 3)).closedAt() == CREATED
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
        def reported = trackedTicket(misses: 2).reported(status, LATER, DEFAULT)

        then:
        reported == trackedTicket(status: status, lastSeenAt: LATER, lastEventAt: LATER, closedAt: closed ? LATER : null)
        trackedTicket(demo: true).reported(status, LATER, DEFAULT).demo()
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

    def "a ticket seen again starts counting its misses afresh"() {
        expect:
        trackedTicket(misses: 3).seen(LATER) == trackedTicket(lastSeenAt: LATER)
    }

    def "a ticket CyberTrack did not find counts the miss and is closed on the #limit. one in a row"() {
        given:
        def followUp = new FollowUp(['Done'], limit)
        def ticket = trackedTicket()

        when:
        (limit - 1).times { ticket = ticket.missed(LATER, followUp) }

        then:
        ticket == trackedTicket(misses: limit - 1)
        ticket.open()

        when:
        ticket = ticket.missed(LATER, followUp)

        then:
        ticket == trackedTicket(misses: limit, closedAt: LATER)
        !ticket.open()

        where:
        limit << [1, 3, 12]
    }
}
