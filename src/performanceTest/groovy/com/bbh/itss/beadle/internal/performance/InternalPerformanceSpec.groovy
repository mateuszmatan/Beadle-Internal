package com.bbh.itss.beadle.internal.performance

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification
import com.bbh.itss.beadle.internal.support.LatencyStats

class InternalPerformanceSpec extends BeadleInternalSpecification {

    def "#path answers 20 calls on 4 threads with a p95 under #limit ms"() {
        when:
        def stats = LatencyStats.measure(20, 4) { api.get(path).status == 200 }

        then:
        stats.errors == 0
        stats.p95 < limit

        where:
        path                                                          || limit
        '/api/internal/status'                                        || 300
        '/api/internal/jira/projects/CERT/versions'                   || 500
        '/api/internal/jira/projects/CERT/epics?fixVersion=CERT%201.4' || 500
        '/api/internal/qtest/projects/1/test-cycles/11/summary'       || 500
        '/api/internal/call-log?size=50'                              || 500
    }
}
