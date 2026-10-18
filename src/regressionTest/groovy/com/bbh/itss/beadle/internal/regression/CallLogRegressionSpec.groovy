package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.Duration
import java.time.Instant

import static java.nio.charset.StandardCharsets.UTF_8

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

    def "the user travels percent-encoded, is cut to what the log holds, and is unknown without the header"() {
        given:
        def caller = anonymous.withHeaders([Authorization: "Bearer $TOKEN".toString(), 'X-Correlation-Id': correlationId])

        when:
        [URLEncoder.encode('Łukasz Wójcik', UTF_8), 'u' * 250, null].each { user ->
            assert (user == null ? caller : caller.withHeaders(['X-Beadle-User': user])).get('/api/internal/qtest/projects')
                    .status == 200
        }

        then:
        callLog()*.userName == ['Łukasz Wójcik', 'u' * 197 + '...', 'unknown']
    }

    def "a correlation id too long to be logged and passed on is refused before any system is called"() {
        when:
        def response = api.withHeaders(['X-Correlation-Id': 'c' * 120]).get('/api/internal/qtest/projects')

        then:
        response.status == 400
        response.json.problems == [[field: 'X-Correlation-Id', message: 'is too long: it may take at most 100 bytes']]
        callLog('c' * 97 + '...') == []
    }
}
