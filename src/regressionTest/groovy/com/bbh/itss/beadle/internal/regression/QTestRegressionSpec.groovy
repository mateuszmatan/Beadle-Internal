package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import java.time.Instant

class QTestRegressionSpec extends BeadleInternalSpecification {

    def "the projects, their test cycles and the runs of a cycle are read with their latest results"() {
        when:
        def projects = api.get('/api/internal/qtest/projects')
        def cycles = api.get('/api/internal/qtest/projects/1/test-cycles')
        def runs = api.get('/api/internal/qtest/projects/1/test-runs?cycleId=11')

        then:
        [projects, cycles, runs]*.status == [200, 200, 200]
        projects.json == [[id: 1, name: 'CertScanner'], [id: 2, name: 'PayHub']]
        cycles.json == [[id: 10, name: 'Release 1.3', pid: 'CL-10'], [id: 11, name: 'Release 1.4', pid: 'CL-11'],
                        [id: 12, name: 'Nightly regression', pid: 'CL-12']]
        runs.json.size() == 34
        runs.json.every { it.keySet() == ['id', 'name', 'pid', 'status', 'executedAt'] as Set && it.pid == "TR-$it.id".toString() }
        runs.json.every { (it.status == 'Unexecuted') == (it.executedAt == null) }
        runs.json.findAll { it.executedAt }.every { Instant.parse(it.executedAt as String).isBefore(clock.instant()) }
    }

    def "the summary of a cycle counts its runs by result"() {
        given:
        def runs = api.get('/api/internal/qtest/projects/1/test-runs?cycleId=11').json as List<Map>

        when:
        def summary = api.get('/api/internal/qtest/projects/1/test-cycles/11/summary')

        then:
        summary.status == 200
        summary.json == [total     : runs.size(), passed: runs.count { it.status == 'Passed' },
                         failed    : runs.count { it.status == 'Failed' }, blocked: runs.count { it.status == 'Blocked' },
                         unexecuted: runs.count { it.status == 'Unexecuted' }]
        summary.json.unexecuted > 0

        and:
        callLog()*.subMap('system', 'operation', 'target', 'outcome') == [
                [system: 'QTEST', operation: 'runs', target: '1 11', outcome: 'SUCCESS'],
                [system: 'QTEST', operation: 'summary', target: '1 11', outcome: 'SUCCESS']]
    }

    def "#what is #answer"() {
        when:
        def response = api.get(path)

        then:
        response.status == status
        response.header('Content-Type').startsWith('application/problem+json')

        where:
        what                          | path                                                    || status | answer
        'a run list without a cycle'  | '/api/internal/qtest/projects/1/test-runs'              || 400    | 'refused'
        'an unknown project'          | '/api/internal/qtest/projects/9/test-cycles'            || 404    | 'not found'
        'an unknown cycle'            | '/api/internal/qtest/projects/1/test-cycles/99/summary' || 404    | 'not found'
        'a project that is no number' | '/api/internal/qtest/projects/cert/test-cycles'         || 400    | 'refused'
    }
}
