package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant

class CyberTrackRegressionSpec extends BeadleInternalSpecification {

    static final Duration STEP = Duration.ofMinutes(2)

    def conditions = new PollingConditions(timeout: 45, delay: 0.5)

    def "a secure coding ticket is created in the SCP project and read back as To Do"() {
        when:
        def created = api.post('/api/internal/cybertrack/tickets', ticketJson())

        then:
        created.status == 201
        created.json.key ==~ /SCP-\d+/
        created.json == [key: created.json.key, url: null]
        created.header('Location') == "$api.baseUrl/api/internal/cybertrack/tickets/$created.json.key".toString()

        when:
        def read = api.get("/api/internal/cybertrack/tickets/$created.json.key")

        then:
        read.status == 200
        read.json == [key: created.json.key, status: 'To Do', url: null, updated: clock.instant().toString()]

        and: 'the creation and the reads are in the call log of the request, the ticket is followed from To Do'
        callLog()*.subMap('system', 'operation', 'target', 'outcome', 'userName') == [
                [system: 'CYBERTRACK', operation: 'create', target: 'CHG0031001', outcome: 'SUCCESS', userName: USER],
                [system: 'CYBERTRACK', operation: 'read', target: created.json.key, outcome: 'SUCCESS', userName: USER],
                [system: 'CYBERTRACK', operation: 'read', target: created.json.key, outcome: 'SUCCESS', userName: USER]]
        followed(created.json.key as String) == [STATUS: 'To Do', CORRELATION_ID: correlationId, CLOSED: false]
    }

    def "a ticket request with #problem is refused naming the fields, without calling CyberTrack"() {
        when:
        def response = api.post('/api/internal/cybertrack/tickets', body)

        then:
        response.status == 400
        response.header('Content-Type').startsWith('application/problem+json')
        response.json.title == 'Validation failed'
        response.json.problems*.field == fields
        callLog() == []

        where:
        problem                | body                                                              || fields
        'nothing in it'        | [:]                                                               || ['changeNumber', 'applicationName', 'implementationDate', 'inputs.apoNumber', 'inputs.bitbucketUrl', 'inputs.artifactLink', 'inputs.qcApplicationLink']
        'a link that is not one' | ticketJson(inputs: ticketJson().inputs + [artifactLink: 'nexus/cert']) || ['inputs.artifactLink']
    }

    def "a ticket CyberTrack does not have is not found"() {
        when:
        def response = api.get('/api/internal/cybertrack/tickets/CERT-77')

        then:
        response.status == 404
        response.json.detail == 'CyberTrack ticket CERT-77 does not exist'
        callLog()*.subMap('operation', 'outcome', 'message') ==
                [[operation: 'read', outcome: 'FAILURE', message: 'CyberTrack ticket CERT-77 does not exist']]
    }

    def "Beadle-GUI hears of every status a ticket moves to, with the service token and the correlation id of its creation"() {
        given:
        Instant createdAt = clock.instant()
        String key = api.post('/api/internal/cybertrack/tickets', ticketJson()).json.key

        when:
        clock.advance(STEP)

        then:
        conditions.eventually {
            assert GUI.callbacksFor(key)*.body*.status == ['In Progress']
        }
        with(GUI.callbacksFor(key).first()) {
            method == 'POST'
            path == '/api/callbacks/internal'
            header('Authorization') == "Bearer $TOKEN".toString()
            header('X-Beadle-User') == 'system'
            header('X-Correlation-Id') == correlationId
            header('Content-Type').startsWith('application/json')
            body == [system: 'CYBERTRACK', key: key, status: 'In Progress', at: (createdAt + STEP).toString(),
                     correlationId: correlationId]
            status == 202
        }
        api.get("/api/internal/cybertrack/tickets/$key").json.status == 'In Progress'

        when:
        clock.advance(STEP)

        then:
        conditions.eventually {
            assert GUI.callbacksFor(key)*.body*.status == ['In Progress', 'Done']
        }
        GUI.callbacksFor(key)[1].body.at == (createdAt + STEP.multipliedBy(2)).toString()
        followed(key) == [STATUS: 'Done', CORRELATION_ID: correlationId, CLOSED: true]

        and: 'the callbacks are in the call log of the creation, as calls of the system to Beadle-GUI'
        callLog().findAll { it.system == 'GUI' }*.subMap('userName', 'operation', 'target', 'outcome') == [
                [userName: 'system', operation: 'notify', target: "$key In Progress".toString(), outcome: 'SUCCESS'],
                [userName: 'system', operation: 'notify', target: "$key Done".toString(), outcome: 'SUCCESS']]
    }

    def "a callback Beadle-GUI did not take is sent again on the next tick"() {
        given:
        String key = api.post('/api/internal/cybertrack/tickets', ticketJson()).json.key
        GUI.refuse(key, 1)

        when:
        clock.advance(STEP)

        then:
        conditions.eventually {
            assert GUI.callbacksFor(key)*.status == [503, 202]
        }
        GUI.callbacksFor(key)*.body*.status == ['In Progress', 'In Progress']
        followed(key).STATUS == 'In Progress'
        callLog().findAll { it.system == 'GUI' }*.outcome == ['FAILURE', 'SUCCESS']
        callLog().find { it.system == 'GUI' }.message.startsWith("Beadle-GUI could not take the callback for $key: 503")
    }

    private Map followed(String key) {
        def row = jdbc.queryForMap('''SELECT STATUS, CORRELATION_ID, CLOSED_AT FROM BEADLE_INTERNAL_CYBERTRACK_TICKET
                WHERE TICKET_KEY = ?''', key)
        [STATUS: row.STATUS, CORRELATION_ID: row.CORRELATION_ID, CLOSED: row.CLOSED_AT != null]
    }

    private static Map ticketJson(Map changes = [:]) {
        [changeNumber: 'CHG0031001', applicationName: 'CertScanner', implementationDate: '2026-10-20',
         inputs      : [apoNumber   : 'APO-12345', bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                        artifactLink: 'https://nexus.bbh.com/cert/cert-1.4.jar', qcApplicationLink: 'https://cert.qc.bbh.com']] + changes
    }
}
