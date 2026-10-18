package com.bbh.itss.beadle.internal.application

import com.bbh.itss.beadle.internal.application.calllog.CallLogService
import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.internal.application.confluence.ConfluenceService
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.application.cybertrack.CyberTrackService
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.application.jira.JiraService
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.application.qtest.QTestService
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.application.status.StatusService
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.domain.status.ServiceStatus
import org.slf4j.MDC
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors

import static com.bbh.itss.beadle.internal.domain.status.SystemStatus.of
import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry
import static com.bbh.itss.beadle.internal.support.Fixtures.cycle
import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.issue
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.project
import static com.bbh.itss.beadle.internal.support.Fixtures.run
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static com.bbh.itss.beadle.internal.support.Fixtures.trackedTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class UseCasesSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:30:00Z')
    static final Caller USER = new Caller('Mateusz Matan', 'corr-1')

    JiraPort jira = Mock()
    ConfluencePort confluence = Mock()
    QTestPort qtest = Mock()
    CyberTrackPort cyberTrack = Mock()
    TrackedTicketPort tracked = Mock()
    CallLogPort callLog = Mock()

    def "the versions of a project come unreleased first, newest first, then the released ones newest first"() {
        given:
        def released = version(name: 'CERT 1.2', released: true, releaseDate: LocalDate.parse('2026-08-01'))
        def older = version(name: 'CERT 1.1', released: true, releaseDate: LocalDate.parse('2026-06-01'))
        def planned = version(name: 'CERT 1.4')
        def unplanned = version(name: 'CERT 1.5', releaseDate: null)

        when:
        def versions = new JiraService(jira).versions(' CERT ')

        then:
        1 * jira.versions('CERT') >> [older, planned, released, unplanned]
        versions == [unplanned, planned, released, older]
    }

    def "the epics and stories of a FixVersion are asked trimmed, and no epic key asks for no story"() {
        given:
        def service = new JiraService(jira)

        when:
        def epics = service.epics('CERT', ' CERT 1.4 ')
        def stories = service.stories('CERT', 'CERT 1.4', [' CERT-12', '', null, 'CERT-12', 'CERT-13'])
        def none = service.stories('CERT', 'CERT 1.4', [' '])

        then:
        1 * jira.epics('CERT', 'CERT 1.4') >> [issue()]
        1 * jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) >> [issue(key: 'CERT-20', epicKey: 'CERT-12')]
        0 * jira._
        epics == [issue()]
        stories*.key() == ['CERT-20']
        none == []
    }

    def "a request without #field is refused before the system is asked"() {
        when:
        call(new JiraService(jira), new ConfluenceService(confluence), new QTestService(qtest))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        0 * _

        where:
        call                                              || fields
        ({ j, c, q -> j.versions(' ') })                  || ['key']
        ({ j, c, q -> j.epics('CERT', null) })            || ['fixVersion']
        ({ j, c, q -> j.stories('CERT', '', ['CERT-1']) }) || ['fixVersion']
        ({ j, c, q -> c.find(null, ' ') })                || ['spaceKey', 'title']
        ({ j, c, q -> q.runs(1, null) })                  || ['cycleId']
        field = fields.join(' and ')
    }

    def "Confluence pages are found without their bodies, read, created and updated by their trimmed id"() {
        given:
        def service = new ConfluenceService(confluence)

        when:
        def found = service.find(' DSO ', ' Beadle release notes ')
        def read = service.get(' 100001 ')
        def created = service.create(newPage())
        def updated = service.update(' 100001 ', pageUpdate())

        then:
        1 * confluence.find('DSO', 'Beadle release notes') >> [page()]
        1 * confluence.get('100001') >> page()
        1 * confluence.create(newPage()) >> page(id: '100009', version: 1)
        1 * confluence.update('100001', pageUpdate()) >> page(version: 4)
        found == [page(body: null)]
        read == page()
        created.id() == '100009'
        updated.version() == 4
    }

    def "a Confluence page or CyberTrack ticket without an id is not found without asking"() {
        when:
        lookup(new ConfluenceService(confluence), new CyberTrackService(cyberTrack, tracked, fixed(NOW, UTC)))

        then:
        thrown(NoSuchElementException)
        0 * _

        where:
        lookup << [{ c, t -> c.get(' ') }, { c, t -> c.update(null, pageUpdate()) }, { c, t -> t.read('  ') }]
    }

    def "qTest, the call log and the status of the systems are read as they are"() {
        given:
        def query = CallLogQuery.of('corr-1', null, null, 0, 20)
        def entries = new CallLogPage(0, 20, 1, [callLogEntry()])
        jira.connected() >> true
        cyberTrack.connected() >> false
        confluence.connected() >> true
        qtest.connected() >> false

        when:
        def service = new QTestService(qtest)
        def answers = [service.projects(), service.cycles(1), service.runs(1, 10), service.summary(1, 10),
                       new CallLogService(callLog).find(query)]

        then:
        1 * qtest.projects() >> [project()]
        1 * qtest.cycles(1) >> [cycle()]
        1 * qtest.runs(1, 10) >> [run()]
        1 * qtest.summary(1, 10) >> new QTestSummary(1, 1, 0, 0, 0)
        1 * callLog.find(query) >> entries
        answers == [[project()], [cycle()], [run()], new QTestSummary(1, 1, 0, 0, 0), entries]
        new StatusService(jira, cyberTrack, confluence, qtest).status() == new ServiceStatus(of(true), of(false), of(true), of(false))
    }

    def "a secure coding ticket is created, answered at once and followed with the caller's correlation id"() {
        given:
        def service = new CyberTrackService(cyberTrack, tracked, fixed(NOW, UTC))
        def created = new CreatedTicket('SCP-1234', 'https://cybertrack.bbh.com/browse/SCP-1234')

        when:
        def result = CallerContext.as(USER) { service.create(ticket()) }

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.empty()

        then:
        1 * cyberTrack.create(ticket()) >> created

        then:
        1 * tracked.track(TrackedTicket.created(created, 'CHG0031001', 'corr-1', NOW))
        result == created
    }

    def "a change asking again gets the ticket it has open, without a new one"() {
        when:
        def result = new CyberTrackService(cyberTrack, tracked, fixed(NOW, UTC)).create(ticket())

        then:
        1 * tracked.openFor('CHG0031001') >> Optional.of(trackedTicket())
        0 * cyberTrack.create(_)
        0 * tracked.track(_)
        result == trackedTicket().asCreated()
    }

    def "two requests for the same change at once create one ticket, and a ticket CyberTrack refused is not followed"() {
        given:
        Map<String, TrackedTicket> followed = [:].asSynchronized()
        def memory = [openFor: { String change -> Optional.ofNullable(followed.values().find { it.changeNumber() == change }) },
                      track  : { TrackedTicket ticket -> followed[ticket.key()] = ticket }] as TrackedTicketPort
        int created = 0
        def slow = [connected: { true },
                    create   : { SecureCodingTicket ticket ->
                        Thread.sleep(100)
                        if (ticket.changeNumber() == 'CHG9') {
                            throw new UncheckedIOException('CyberTrack could not create: 400', new IOException())
                        }
                        new CreatedTicket('SCP-' + (1234 + created++), null)
                    }] as CyberTrackPort
        def service = new CyberTrackService(slow, memory, fixed(NOW, UTC))
        def pool = Executors.newFixedThreadPool(2)

        when:
        def answers = [pool.submit({ service.create(ticket()) } as Callable),
                       pool.submit({ service.create(ticket()) } as Callable)]*.get()
        service.create(ticket(changeNumber: 'CHG9'))

        then:
        thrown(UncheckedIOException)
        created == 1
        answers*.key() == ['SCP-1234', 'SCP-1234']
        followed.keySet() == ['SCP-1234'] as Set

        cleanup:
        pool.shutdown()
    }

    def "a ticket is read by its trimmed key"() {
        when:
        def found = new CyberTrackService(cyberTrack, tracked, fixed(NOW, UTC)).read(' SCP-1234 ')

        then:
        1 * cyberTrack.read('SCP-1234') >> cyberTrackTicket()
        found == cyberTrackTicket()
    }

    def "work runs as a caller, in the logging MDC too, and the caller before comes back afterwards, even on a failure"() {
        given:
        def system = new Caller('system', 'tick-1')

        expect:
        CallerContext.current() == Caller.NONE
        CallerContext.as(USER) {
            assert CallerContext.as(system) { [CallerContext.current(), MDC.get('correlationId')] } == [system, 'tick-1']
            Caller elsewhere = null
            Thread.start { elsewhere = CallerContext.current() }.join()
            assert elsewhere == Caller.NONE
            [CallerContext.current(), MDC.get('correlationId')]
        } == [USER, 'corr-1']
        CallerContext.current() == Caller.NONE
        MDC.get('correlationId') == null

        when:
        CallerContext.run(system) { throw new IllegalStateException('boom') }

        then:
        thrown(IllegalStateException)
        CallerContext.current() == Caller.NONE
    }
}
