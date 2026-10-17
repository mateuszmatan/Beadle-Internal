package com.bbh.itss.beadle.internal.smoke

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

class InternalSmokeSpec extends BeadleInternalSpecification {

    def "the service reports UP on health endpoints"() {
        when:
        def health = anonymous.get('/actuator/health')
        def liveness = anonymous.get('/actuator/health/liveness')
        def readiness = anonymous.get('/actuator/health/readiness')

        then:
        health.status == 200
        health.json.status == 'UP'
        liveness.status == 200
        liveness.json.status == 'UP'
        readiness.status == 200
        readiness.json.status == 'UP'
    }

    def "the service reports internal system statuses and serves call log"() {
        when:
        def status = api.get('/api/internal/status')
        def callLog = api.get('/api/internal/call-log?page=0&size=10')

        then:
        status.status == 200
        status.json.containsKey('jira')
        status.json.containsKey('confluence')
        status.json.containsKey('cyberTrack')
        status.json.containsKey('qtest')
        callLog.status == 200
    }
}
