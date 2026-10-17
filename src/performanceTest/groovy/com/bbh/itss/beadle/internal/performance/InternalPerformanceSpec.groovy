package com.bbh.itss.beadle.internal.performance

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification
import com.bbh.itss.beadle.internal.support.LatencyStats

class InternalPerformanceSpec extends BeadleInternalSpecification {

    def "status, jira and call log respond with low p95 latencies"() {
        when:
        def statusStats = LatencyStats.measure([calls: 30, threads: 4], 'GET /api/internal/status') {
            api.get('/api/internal/status').status == 200
        }
        def jiraStats = LatencyStats.measure([calls: 20, threads: 2], 'GET /api/internal/jira/projects/BDL/versions') {
            api.get('/api/internal/jira/projects/BDL/versions').status == 200
        }
        def callLogStats = LatencyStats.measure([calls: 20, threads: 2], 'GET /api/internal/call-log') {
            api.get('/api/internal/call-log?page=0&size=10').status == 200
        }

        then:
        statusStats.p95 < 500
        jiraStats.p95 < 500
        callLogStats.p95 < 600
        statusStats.errors == 0
        jiraStats.errors == 0
        callLogStats.errors == 0
    }
}
