package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.calllog.port.in.CallLogUseCase
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import java.time.Instant

import static com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery.SIZE_MESSAGE
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class CallLogControllerSpec extends ControllerSpecification {

    static final Instant NOON = Instant.parse('2026-10-15T12:00:00Z')

    CallLogUseCase callLog = Mock()

    @Override
    Object controller() {
        new CallLogController(callLog)
    }

    def "the call log is searched by correlation id and time window, page by page"() {
        when:
        def response = call(get('/api/internal/call-log').param('correlationId', 'corr-1')
                .param('from', '2026-10-15T12:00:00Z').param('to', '2026-10-15T13:00:00Z').param('page', '1').param('size', '2'))

        then:
        1 * callLog.find(new CallLogQuery('corr-1', NOON, NOON.plusSeconds(3600), 1, 2)) >>
                new CallLogPage(1, 2, 5, [callLogEntry(), callLogEntry(id: 2L, system: CONFLUENCE, operation: 'update',
                        target: '100001 Notes', outcome: FAILURE, message: 'Confluence could not update the page 100001: 409',
                        durationMs: 345L, userName: null)])
        response.status == 200
        bodyOf(response) == [page: 1, size: 2, total: 5, entries: [
                [id: 1, at: '2026-10-15T09:00:00Z', userName: 'Mateusz Matan', correlationId: 'corr-1', system: 'JIRA',
                 operation: 'versions', target: 'CERT', outcome: 'SUCCESS', message: null, durationMs: 12],
                [id: 2, at: '2026-10-15T09:00:00Z', userName: null, correlationId: 'corr-1', system: 'CONFLUENCE',
                 operation: 'update', target: '100001 Notes', outcome: 'FAILURE',
                 message: 'Confluence could not update the page 100001: 409', durationMs: 345]]]
    }

    def "without parameters the first page of 50 entries is listed"() {
        when:
        def response = call(get('/api/internal/call-log'))

        then:
        1 * callLog.find(new CallLogQuery(null, null, null, 0, 50)) >> new CallLogPage(0, 50, 0, [])
        response.status == 200
        bodyOf(response) == [page: 0, size: 50, total: 0, entries: []]
    }

    def "a page size out of range is refused with the problem and a time that is no instant is malformed"() {
        when:
        def refused = call(get('/api/internal/call-log').param('size', '0'))
        def malformed = call(get('/api/internal/call-log').param('from', '2026-10-15'))

        then:
        0 * callLog._
        refused.status == 400
        bodyOf(refused).problems == [[field: 'size', message: SIZE_MESSAGE]]
        malformed.status == 400
        bodyOf(malformed).detail == "The value given for 'from' is not one this endpoint can read."
    }

    def "without the service token the call log is 401 (#authorization)"() {
        when:
        def response = callWithout(get('/api/internal/call-log'), authorization)

        then:
        response.status == 401
        0 * callLog._

        where:
        authorization << unauthorized()
    }
}
