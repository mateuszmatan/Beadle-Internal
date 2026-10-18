package com.bbh.itss.beadle.internal.smoke

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

/**
 * What Beadle-GUI needs of Beadle-Internal: its health, the status, the Jira versions and a secure coding ticket.
 */
class InternalSmokeSpec extends BeadleInternalSpecification {

    static final Map DEMO = [connected: false, mode: 'demo']

    def "the health, liveness and readiness are UP and open without a token"() {
        expect:
        ['/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness'].every {
            def health = anonymous.get(it)
            health.status == 200 && health.json.status == 'UP'
        }
    }

    def "the status answers, a Jira project lists its versions and a secure coding ticket is created, with the token only"() {
        when:
        def created = api.post('/api/internal/cybertrack/tickets', ticket())

        then:
        api.get('/api/internal/status').json == [jira: DEMO, cyberTrack: DEMO, confluence: DEMO, qtest: DEMO]
        !api.get('/api/internal/jira/projects/CERT/versions').json.empty
        created.status == 201
        created.json.key ==~ /SCP-\d+/
        anonymous.get('/api/internal/status').status == 401
    }
}
