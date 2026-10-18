package com.bbh.itss.beadle.internal.smoke

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

class InternalSmokeSpec extends BeadleInternalSpecification {

    static final Map DEMO = [connected: false, mode: 'demo']

    def "the health endpoints are UP and open without a token: #path"() {
        when:
        def health = anonymous.get(path)

        then:
        health.status == 200
        health.json.status == 'UP'

        where:
        path << ['/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness']
    }

    def "the endpoints Beadle-GUI calls answer with the service token and refuse a call without it"() {
        expect:
        api.get('/api/internal/status').json == [jira: DEMO, cyberTrack: DEMO, confluence: DEMO, qtest: DEMO]
        api.get('/api/internal/jira/projects/CERT/versions').status == 200
        api.post('/api/internal/cybertrack/tickets', [changeNumber: 'CHG0030001', applicationName: 'CertScanner',
                                                      implementationDate: '2026-10-20',
                                                      inputs: [apoNumber: 'APO-1', bitbucketUrl: 'https://bitbucket.bbh.com/c',
                                                               artifactLink: 'https://nexus.bbh.com/c.jar',
                                                               qcApplicationLink: 'https://c.qc.bbh.com']]).status == 201
        anonymous.get('/api/internal/status').status == 401
    }
}
