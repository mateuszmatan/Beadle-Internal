package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

/**
 * The reads Beadle-GUI makes for a release: the FixVersions, epics and stories of Jira and the test results of qTest.
 */
class ReadsRegressionSpec extends BeadleInternalSpecification {

    def "the versions of a project come unreleased first, then its epics and the stories of chosen epics are read"() {
        when:
        def versions = api.get('/api/internal/jira/projects/CERT/versions').json as List<Map>
        def version = versions.find { !it.released }.name as String
        def epics = api.get("/api/internal/jira/projects/CERT/epics?fixVersion=${enc(version)}").json as List<Map>
        def chosen = epics*.key.take(2)
        def stories = api.get("/api/internal/jira/projects/CERT/stories?fixVersion=${enc(version)}&epicKeys=${chosen.join(',')}")
                .json as List<Map>

        then:
        versions.every { it.keySet() == ['name', 'released', 'releaseDate'] as Set }
        versions*.released == versions*.released.sort(false)
        !epics.empty
        epics.every { it.keySet() == ['key', 'summary', 'status', 'epicKey', 'updated'] as Set && it.epicKey == null }
        stories.every { it.epicKey in chosen }

        and: 'every call is in the call log with the user and the correlation id of the request'
        callLog()*.subMap('system', 'operation', 'target', 'userName', 'outcome') == [
                [system: 'JIRA', operation: 'versions', target: 'CERT', userName: USER, outcome: 'SUCCESS'],
                [system: 'JIRA', operation: 'epics', target: "CERT $version".toString(), userName: USER, outcome: 'SUCCESS'],
                [system: 'JIRA', operation: 'stories', target: "CERT $version ${chosen.join(',')}".toString(), userName: USER,
                 outcome: 'SUCCESS']]
    }

    def "a Jira read without a FixVersion is refused naming the field, without calling Jira"() {
        when:
        def response = api.get('/api/internal/jira/projects/CERT/epics')

        then:
        response.status == 400
        response.header('Content-Type').startsWith('application/problem+json')
        response.json.problems == [[field: 'fixVersion', message: 'is required']]
        callLog() == []
    }

    def "the qTest projects, the cycles of a project and the runs of a cycle are read, and the summary counts them"() {
        when:
        def projects = api.get('/api/internal/qtest/projects').json
        def cycles = api.get('/api/internal/qtest/projects/1/test-cycles').json
        def runs = api.get('/api/internal/qtest/projects/1/test-runs?cycleId=11').json as List<Map>
        def summary = api.get('/api/internal/qtest/projects/1/test-cycles/11/summary').json

        then:
        projects == [[id: 1, name: 'CertScanner'], [id: 2, name: 'PayHub']]
        cycles*.pid == ['CL-10', 'CL-11', 'CL-12']
        runs.every { it.keySet() == ['id', 'name', 'pid', 'status', 'executedAt'] as Set }
        summary == [total: runs.size(), passed: runs.count { it.status == 'Passed' }, failed: runs.count { it.status == 'Failed' },
                    blocked: runs.count { it.status == 'Blocked' }, unexecuted: runs.count { it.status == 'Unexecuted' }]
        callLog()*.operation == ['projects', 'cycles', 'runs', 'summary']
    }

    def "an unknown qTest cycle is not found and logged as a failure"() {
        when:
        def response = api.get('/api/internal/qtest/projects/1/test-cycles/99/summary')

        then:
        response.status == 404
        response.json.detail == 'qTest test cycle 99 does not exist'
        callLog()*.subMap('operation', 'outcome', 'message') ==
                [[operation: 'summary', outcome: 'FAILURE', message: 'qTest test cycle 99 does not exist']]
    }

    private static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
