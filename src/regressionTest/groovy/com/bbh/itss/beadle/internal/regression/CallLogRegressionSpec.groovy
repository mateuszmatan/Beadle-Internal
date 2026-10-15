package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.Duration
import java.time.Instant

class CallLogRegressionSpec extends BeadleInternalSpecification {

    def "every call to a system fills the call log, newest first, with who caused it and how it went"() {
        given:
        Instant start = clock.instant()
        api.get('/api/internal/jira/projects/CERT/versions')
        clock.advance(Duration.ofMinutes(1))
        api.get('/api/internal/qtest/projects')
        clock.advance(Duration.ofMinutes(1))
        api.get('/api/internal/confluence/pages/999')

        when:
        def page = api.get("/api/internal/call-log?correlationId=$correlationId")

        then:
        page.status == 200
        page.json.subMap('page', 'size', 'total') == [page: 0, size: 50, total: 3]
        page.json.entries*.subMap('at', 'userName', 'correlationId', 'system', 'operation', 'target', 'outcome', 'message') == [
                [at: (start + Duration.ofMinutes(2)).toString(), userName: USER, correlationId: correlationId,
                 system: 'CONFLUENCE', operation: 'get', target: '999', outcome: 'FAILURE',
                 message: 'Confluence page 999 does not exist'],
                [at: (start + Duration.ofMinutes(1)).toString(), userName: USER, correlationId: correlationId,
                 system: 'QTEST', operation: 'projects', target: null, outcome: 'SUCCESS', message: null],
                [at: start.toString(), userName: USER, correlationId: correlationId, system: 'JIRA',
                 operation: 'versions', target: 'CERT', outcome: 'SUCCESS', message: null]]
        page.json.entries.every { it.id instanceof Number && it.durationMs >= 0 }

        when:
        def second = api.get("/api/internal/call-log?correlationId=$correlationId&page=1&size=1")
        def window = api.get("/api/internal/call-log?correlationId=$correlationId&from=${start + Duration.ofMinutes(1)}&to=${start + Duration.ofMinutes(2)}")

        then:
        second.json.subMap('page', 'size', 'total') == [page: 1, size: 1, total: 3]
        second.json.entries*.system == ['QTEST']
        window.json.entries*.system == ['QTEST']
    }

    def "a call without a correlation id is logged under a fresh one"() {
        given:
        def caller = anonymous.withHeaders([Authorization: "Bearer $TOKEN".toString(), 'X-Beadle-User': USER])
        Instant start = clock.instant()
        clock.advance(Duration.ofHours(1))

        when:
        caller.get('/api/internal/qtest/projects/2/test-cycles')
        def logged = api.get("/api/internal/call-log?from=${start + Duration.ofHours(1)}&size=500").json.entries
                .find { it.operation == 'cycles' && it.target == '2' }

        then:
        logged.userName == USER
        logged.correlationId ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/
    }

    def "a call log query with #problem is refused naming the field"() {
        when:
        def response = api.get('/api/internal/call-log?' + query)

        then:
        response.status == 400
        response.json.problems*.field == [field]

        where:
        problem              | query                                                       || field
        'a negative page'    | 'page=-1'                                                   || 'page'
        'a page too large'   | 'size=501'                                                  || 'size'
        'a window backwards' | 'from=2026-10-15T10:00:00Z&to=2026-10-15T09:00:00Z'         || 'from'
    }
}
