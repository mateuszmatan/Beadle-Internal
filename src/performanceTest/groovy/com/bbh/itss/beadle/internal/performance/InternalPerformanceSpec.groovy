package com.bbh.itss.beadle.internal.performance

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification
import com.bbh.itss.beadle.internal.support.LatencyStats

/**
 * The calls Beadle-GUI makes: the Jira reads of a release and the creation of a secure coding ticket.
 */
class InternalPerformanceSpec extends BeadleInternalSpecification {

    static final int LIMIT = 500

    def "the Jira #read answer 20 calls on 2 threads with a p95 under 500 ms"() {
        when:
        def stats = LatencyStats.measure(20, 2) { api.get(path).status == 200 }

        then:
        stats.errors == 0
        stats.p95 < LIMIT

        where:
        read      | path
        'versions' | '/api/internal/jira/projects/CERT/versions'
        'epics'    | '/api/internal/jira/projects/CERT/epics?fixVersion=CERT%201.4'
        'stories'  | '/api/internal/jira/projects/CERT/stories?fixVersion=CERT%201.4&epicKeys=CERT-1,CERT-2'
    }

    def "20 secure coding tickets of different changes are created on 2 threads with a p95 under 500 ms"() {
        when:
        def stats = LatencyStats.measure(20, 2) { api.post('/api/internal/cybertrack/tickets', ticket()).status == 201 }

        then:
        stats.errors == 0
        stats.p95 < LIMIT
    }
}
