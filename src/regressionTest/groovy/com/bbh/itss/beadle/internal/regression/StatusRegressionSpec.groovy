package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

class StatusRegressionSpec extends BeadleInternalSpecification {

    static final Map DEMO = [connected: false, mode: 'demo']

    def "with no system configured every system is played by its demo adapter"() {
        when:
        def response = api.get('/api/internal/status')

        then:
        response.status == 200
        response.header('Content-Type').startsWith('application/json')
        response.json == [jira: DEMO, cyberTrack: DEMO, confluence: DEMO, qtest: DEMO]
        callLog() == []
    }

    def "a call #without is refused as a problem and the API stays closed to it"() {
        given:
        def client = authorization == null ? anonymous : anonymous.withHeaders([Authorization: authorization])

        when:
        def responses = ['/api/internal/status', '/api/internal/jira/projects/CERT/versions',
                         '/api/internal/call-log'].collect { client.get(it) }
        def created = client.post('/api/internal/cybertrack/tickets', [changeNumber: 'CHG0031001'])

        then:
        (responses + created).every { it.status == 401 }
        (responses + created).every { it.header('WWW-Authenticate') == 'Bearer' }
        responses[0].header('Content-Type').startsWith('application/problem+json')
        responses[0].json == [title : 'Unauthorized', status: 401,
                              detail: 'The call needs the Beadle service token as Authorization: Bearer <token>.']

        where:
        without                      | authorization
        'without a token'            | null
        'with a wrong token'         | 'Bearer wrong-token'
        'with the token but no Bearer' | TOKEN
        'with basic credentials'     | 'Basic ' + 'beadle:secret'.bytes.encodeBase64()
    }

    def "the health of the service is open to the platform without a token"() {
        when:
        def health = anonymous.get('/actuator/health')

        then:
        health.status == 200
        health.json.status == 'UP'
    }
}
