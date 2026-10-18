package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.Duration
import java.time.Instant

/**
 * The call log: every call Beadle-Internal makes to a system, with who caused it, searched by correlation id and time.
 */
class CallLogRegressionSpec extends BeadleInternalSpecification {

    def "every call to a system is logged with who caused it and how it went, found newest first, page by page"() {
        given:
        Instant start = clock.instant()
        api.get('/api/internal/jira/projects/CERT/versions')
        clock.advance(Duration.ofMinutes(1))
        api.get('/api/internal/confluence/pages/999')

        when:
        def page = api.get("/api/internal/call-log?correlationId=$correlationId").json
        def second = api.get("/api/internal/call-log?correlationId=$correlationId&page=1&size=1").json
        def window = api.get("/api/internal/call-log?correlationId=$correlationId&from=${start + Duration.ofMinutes(1)}").json

        then:
        page.subMap('page', 'size', 'total') == [page: 0, size: 50, total: 2]
        page.entries*.subMap('at', 'userName', 'correlationId', 'system', 'operation', 'target', 'outcome', 'message') == [
                [at: (start + Duration.ofMinutes(1)).toString(), userName: USER, correlationId: correlationId,
                 system: 'CONFLUENCE', operation: 'get', target: '999', outcome: 'FAILURE',
                 message: 'Confluence page 999 does not exist'],
                [at: start.toString(), userName: USER, correlationId: correlationId, system: 'JIRA',
                 operation: 'versions', target: 'CERT', outcome: 'SUCCESS', message: null]]
        second.entries*.system == ['JIRA']
        window.entries*.system == ['CONFLUENCE']
    }
}
