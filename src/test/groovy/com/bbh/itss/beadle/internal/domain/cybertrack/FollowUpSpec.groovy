package com.bbh.itss.beadle.internal.domain.cybertrack

import spock.lang.Specification

class FollowUpSpec extends Specification {

    def "by default #status #verb a ticket"() {
        expect:
        FollowUp.DEFAULT.closes(status) == closes

        where:
        status        || closes
        'Done'        || true
        ' closed '    || true
        'Resolved'    || true
        'Cancelled'   || true
        'Canceled'    || true
        'Rejected'    || true
        "won't do"    || true
        'In Progress' || false
        'Reopened'    || false
        'Remediated'  || false
        null          || false
        verb = closes ? 'closes' : 'does not close'
    }

    def "the closed statuses of a CyberTrack workflow are its own, trimmed and without blanks"() {
        when:
        def followUp = new FollowUp([' Remediated ', 'Risk Accepted', '', 'Remediated'], 3)

        then:
        followUp.closedStatuses() == ['Remediated', 'Risk Accepted']
        followUp.closes('risk accepted')
        !followUp.closes('Done')
        followUp.maxMisses() == 3
    }

    def "a follow-up needs a closed status and at least one miss"() {
        when:
        new FollowUp(statuses, misses)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == message

        where:
        statuses | misses || message
        []       | 12     || 'beadle.cybertrack.closed-statuses needs at least one status'
        [' ']    | 12     || 'beadle.cybertrack.closed-statuses needs at least one status'
        null     | 12     || 'beadle.cybertrack.closed-statuses needs at least one status'
        ['Done'] | 0      || 'beadle.cybertrack.max-misses must be 1 or more, not 0'
    }
}
