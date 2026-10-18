package com.bbh.itss.beadle.internal.domain

import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.PAGE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.RANGE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.SIZE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate.VERSION_MESSAGE
import static com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding.LINK_MESSAGE
import static com.bbh.itss.beadle.internal.domain.shared.Text.abbreviateBytes
import static com.bbh.itss.beadle.internal.domain.shared.Text.bytes
import static com.bbh.itss.beadle.internal.domain.shared.Text.clean
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now
import static com.bbh.itss.beadle.internal.support.Fixtures.CREATED
import static com.bbh.itss.beadle.internal.support.Fixtures.IMPLEMENTATION_DATE
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.run
import static com.bbh.itss.beadle.internal.support.Fixtures.secureCoding
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class DomainSpec extends Specification {

    static final Instant LATER = Instant.parse('2026-10-15T09:30:00Z')
    static final FollowUp FOLLOW_UP = new FollowUp(['Done', 'Closed', 'Resolved', "Won't Do"], 3)
    static final String REQUIRED = 'is required'

    def "a secure coding ticket is named APO-ID_APP-NAME-MMDDYYYY, cut to what CyberTrack takes, and describes its inputs"() {
        when:
        def ticket = SecureCodingTicket.of(' CHG0031001 ', ' CertScanner ', IMPLEMENTATION_DATE, secureCoding())

        then:
        ticket == new SecureCodingTicket('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE, secureCoding())
        ticket.summary() == 'APO-12345_CertScanner-10152026'
        ticket.description() == '''\
            APO number: APO-12345
            Application: CertScanner
            Implementation date: 10152026
            Bitbucket URL (SAST scan): https://bitbucket.bbh.com/projects/CERT/repos/cert
            Artifact link (OSA - Nexus IQ scan): https://jenkins.bbh.com/job/CERT/job/cert-release/
            QC application link (DAST scan): https://cert.qc.bbh.com
            ProTech change: CHG0031001'''.stripIndent()
        ticket.callTarget() == 'CHG0031001'
        bytes(SecureCodingTicket.of('CHG1', 'ł' * 120, IMPLEMENTATION_DATE, secureCoding()).summary()) <= SecureCodingTicket.SUMMARY_MAX
    }

    def "a secure coding ticket #refusal is refused naming the fields"() {
        when:
        SecureCodingTicket.of(changeNumber, application, IMPLEMENTATION_DATE, inputs)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        refusal                      | changeNumber | application   | inputs                                                  || fields                    | messages
        'without a change number'    | ' '          | 'CertScanner' | secureCoding()                                          || ['changeNumber']          | [REQUIRED]
        'with a change number too long' | 'C' * 41  | 'CertScanner' | secureCoding()                                          || ['changeNumber']          | ['is too long: it may take at most 40 bytes']
        'with an application too long' | 'CHG1'     | 'ł' * 128     | secureCoding()                                          || ['applicationName']       | ['is too long: it may take at most 255 bytes']
        'with an input that is no link' | 'CHG1'    | 'CertScanner' | secureCoding(qcApplicationLink: 'cert.qc.bbh.com')      || ['inputs.qcApplicationLink'] | [LINK_MESSAGE]
        'without inputs'             | 'CHG1'       | null          | null                                                    || ['applicationName', 'inputs.apoNumber', 'inputs.bitbucketUrl', 'inputs.artifactLink', 'inputs.qcApplicationLink'] | [REQUIRED] * 5
    }

    def "a ticket without an implementation date is refused, and blank inputs count as missing"() {
        when:
        SecureCodingTicket.of('CHG1', 'CertScanner', null, new SecureCoding(' ', 'https://b', 'https://a', 'https://q'))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['implementationDate', 'inputs.apoNumber']
        refused.message == '2 fields are invalid'
    }

    def "a created ticket is followed without a status until the first poll tells it"() {
        given:
        def created = new CreatedTicket('SCP-1234', 'https://cybertrack.bbh.com/browse/SCP-1234')

        expect:
        TrackedTicket.created(created, 'CHG0031001', 'corr-1', CREATED) == trackedTicket(status: null, lastSeenAt: null)
        trackedTicket().asCreated() == created
        trackedTicket().open()
    }

    def "a ticket moved when CyberTrack tells another status than #known: #current"() {
        expect:
        trackedTicket(status: known).movedIn(cyberTrackTicket(status: current)) == moved

        where:
        known   | current       || moved
        'To Do' | 'To Do'       || false
        'To Do' | 'In Progress' || true
        null    | 'To Do'       || true
        'To Do' | null          || false
    }

    def "a reported status #status is the new known one and #verb the ticket"() {
        when:
        def reported = trackedTicket(misses: 2).reported(status, LATER, FOLLOW_UP)

        then:
        reported == trackedTicket(status: status, lastSeenAt: LATER, lastEventAt: LATER, closedAt: closes ? LATER : null)
        reported.open() == !closes

        where:
        status        || closes
        'In Progress' || false
        ' done '      || true
        "won't do"    || true
        'Remediated'  || false
        verb = closes ? 'closes' : 'does not close'
    }

    def "a ticket seen again counts its misses afresh, and is closed after #limit misses in a row"() {
        given:
        def followUp = new FollowUp(['Done'], limit)
        def ticket = trackedTicket(misses: 3).seen(LATER)

        expect:
        ticket == trackedTicket(lastSeenAt: LATER)

        when:
        (limit - 1).times { ticket = ticket.missed(LATER, followUp) }

        then:
        ticket.open()
        ticket.misses() == limit - 1

        when:
        ticket = ticket.missed(LATER, followUp)

        then:
        ticket == trackedTicket(lastSeenAt: LATER, misses: limit, closedAt: LATER)

        where:
        limit << [1, 3]
    }

    def "the closed statuses of a follow-up are trimmed and compared without case, and it needs one and a miss"() {
        expect:
        new FollowUp([' Remediated ', 'Risk Accepted', '', 'Remediated'], 3).closedStatuses() == ['Remediated', 'Risk Accepted']
        !FOLLOW_UP.closes(null)

        when:
        new FollowUp(statuses, misses)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == message

        where:
        statuses | misses || message
        [' ']    | 12     || 'beadle.cybertrack.closed-statuses needs at least one status'
        null     | 12     || 'beadle.cybertrack.closed-statuses needs at least one status'
        ['Done'] | 0      || 'beadle.cybertrack.max-misses must be 1 or more, not 0'
    }

    def "a call log query takes the first page of 50 entries by default, and one with #problem is refused"() {
        expect:
        CallLogQuery.of(' corr-1 ', null, null, null, null) == new CallLogQuery('corr-1', null, null, 0, 50)

        when:
        CallLogQuery.of(null, from, LATER, page, size)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        problem            | from                 | page | size || fields                   | messages
        'a negative page'  | null                 | -1   | 10   || ['page']                 | [PAGE_MESSAGE]
        'too many entries' | null                 | 0    | 501  || ['size']                 | [SIZE_MESSAGE]
        'everything wrong' | LATER.plusSeconds(1) | -2   | 0    || ['page', 'size', 'from'] | [PAGE_MESSAGE, SIZE_MESSAGE, RANGE_MESSAGE]
    }

    def "a new Confluence page and an update are trimmed, and #problem is refused"() {
        expect:
        NewConfluencePage.of(' DSO ', ' Notes ', ' ', '<p/>') == new NewConfluencePage('DSO', 'Notes', null, '<p/>')
        NewConfluencePage.of('DSO', 'Notes', ' 100001 ', '<p/>').callTarget() == 'DSO/Notes'
        ConfluencePageUpdate.of(' Beadle release notes ', '<p>Beadle 1.1</p>', 3) == pageUpdate()

        when:
        create()

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == messages

        where:
        problem                        | create                                             || fields                        | messages
        'a page without anything'      | { NewConfluencePage.of(null, ' ', null, ' ') }      || ['spaceKey', 'title', 'body'] | [REQUIRED] * 3
        'a page title of 256 bytes'    | { NewConfluencePage.of('DSO', 'ł' * 128, null, 'x') } || ['title']                 | ['is too long: it may take at most 255 bytes']
        'an update without a version'  | { ConfluencePageUpdate.of('Notes', '<p/>', null) }  || ['version']                   | [REQUIRED]
        'an update of version 0'       | { ConfluencePageUpdate.of(null, '', 0) }            || ['title', 'body', 'version']  | [REQUIRED, REQUIRED, VERSION_MESSAGE]
    }

    def "an update replaces the version it names with the next one"() {
        expect:
        pageUpdate().replaces(page(version: 3))
        !pageUpdate().replaces(page(version: 4))
        page().replacedBy(pageUpdate()) == page(version: 4, body: '<p>Beadle 1.1</p>')
        page().withoutBody() == page(body: null)
        pageUpdate().callTarget() == 'Beadle release notes'
    }

    def "the qTest summary counts the runs by status whatever the case, the rest as unexecuted"() {
        expect:
        QTestSummary.of([run(status: 'Passed'), run(status: 'PASSED'), run(status: 'Failed'), run(status: 'blocked'),
                         run(status: 'Incomplete'), run(status: null)]) == new QTestSummary(6, 2, 1, 1, 2)
    }

    def "texts are cleaned of blanks and duplicates, and cut to the UTF-8 bytes of a column on a character boundary"() {
        expect:
        clean(['b', 'a', ' b', '', null]) == ['b', 'a']
        clean(null) == []
        abbreviateBytes(text, max) == cut

        where:
        text         | max || cut
        null         | 5   || null
        'abcdef'     | 6   || 'abcdef'
        'abc  defgh' | 8   || 'abc...'
        'ąąąąą'      | 9   || 'ąąą...'
        'ab😀cd'     | 7   || 'ab...'
    }

    def "a timestamp keeps the microseconds the database stores"() {
        expect:
        now(fixed(Instant.parse('2026-10-05T10:15:30.123456789Z'), UTC)) == Instant.parse('2026-10-05T10:15:30.123456Z')
    }

    def "validation problems carry the path of their view and say how many fields are invalid"() {
        given:
        def problems = new ValidationProblems()

        expect:
        problems.throwIfAny() == null

        when:
        problems.add('code', 'is taken')
        problems.at('services[0]').at('build').add('javaPath', REQUIRED)
        problems.throwIfAny()

        then:
        def refused = thrown(InvalidRequestException)
        refused.message == '2 fields are invalid'
        refused.problems*.field == ['code', 'services[0].build.javaPath']
        InvalidRequestException.of('range', 'use 30d').message == 'use 30d'
    }
}
