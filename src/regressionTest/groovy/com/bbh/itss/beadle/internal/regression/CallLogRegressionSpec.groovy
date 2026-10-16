package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.internal.support.Fixtures.ticketJson

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

    def "a user too long for the call log is cut to it, and a call without a user is logged as unknown"() {
        given:
        def longUser = anonymous.withHeaders([Authorization: "Bearer $TOKEN".toString(), 'X-Beadle-User': 'u' * 250,
                                              'X-Correlation-Id': correlationId])
        def noUser = anonymous.withHeaders([Authorization: "Bearer $TOKEN".toString(), 'X-Correlation-Id': correlationId])

        when:
        def first = longUser.get('/api/internal/qtest/projects')
        def second = noUser.get('/api/internal/qtest/projects/1/test-cycles')

        then:
        first.status == 200
        second.status == 200
        callLog()*.userName == ['u' * 197 + '...', 'unknown']
    }

    def "a user whose name has Polish letters is logged by that name, sent percent-encoded as the contract says"() {
        given:
        String user = 'Łukasz Wójcik'

        when:
        def found = api.withHeaders(['X-Beadle-User': URLEncoder.encode(user, 'UTF-8')]).get('/api/internal/qtest/projects')

        then:
        found.status == 200
        callLog()*.userName == [user]
    }

    def "a correlation id too long to be logged and passed on is refused before any system is called"() {
        given:
        def caller = anonymous.withHeaders([Authorization: "Bearer $TOKEN".toString(), 'X-Beadle-User': USER,
                                            'X-Correlation-Id': 'c' * 120])

        when:
        def response = caller.post('/api/internal/cybertrack/tickets', ticketJson(changeNumber: 'CHG0031999'))

        then:
        response.status == 400
        response.json.problems == [[field: 'X-Correlation-Id', message: 'is too long: it may take at most 100 bytes']]
        callLog('c' * 120) == []
        callLog('c' * 97 + '...') == []
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
