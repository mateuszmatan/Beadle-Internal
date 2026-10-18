package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant

/**
 * The secure coding ticket of a change: created in the demo CyberTrack, then followed by the poll, which tells
 * Beadle-GUI every status the ticket moves to.
 */
class CyberTrackRegressionSpec extends BeadleInternalSpecification {

    static final Duration STEP = Duration.ofMinutes(2)

    def conditions = new PollingConditions(timeout: 10, delay: 0.1)

    def "a ticket is created at once, asked again it is the same one, and read back as To Do"() {
        given:
        def request = ticket()

        when:
        def created = api.post('/api/internal/cybertrack/tickets', request)
        def again = api.post('/api/internal/cybertrack/tickets', request)
        def read = api.get("/api/internal/cybertrack/tickets/$created.json.key")

        then:
        created.status == 201
        created.json.key ==~ /SCP-\d+/
        created.header('Location') == "$api.baseUrl/api/internal/cybertrack/tickets/$created.json.key".toString()
        again.json == created.json
        read.json == [key: created.json.key, status: 'To Do', url: null, updated: clock.instant().toString()]
        callLog().findAll { it.system == 'CYBERTRACK' }*.subMap('system', 'operation', 'target', 'outcome') == [
                [system: 'CYBERTRACK', operation: 'create', target: request.changeNumber, outcome: 'SUCCESS'],
                [system: 'CYBERTRACK', operation: 'read', target: created.json.key, outcome: 'SUCCESS']]
    }

    def "Beadle-GUI hears of every status a ticket moves to, with the correlation id of its creation in the header"() {
        given:
        Instant createdAt = clock.instant()
        String key = api.post('/api/internal/cybertrack/tickets', ticket()).json.key

        expect: 'the first poll tells the status the ticket starts in'
        conditions.eventually {
            assert GUI.callbacksFor(key)*.body*.status == ['To Do']
        }

        when:
        clock.advance(STEP)

        then:
        conditions.eventually {
            assert GUI.callbacksFor(key)*.body*.status == ['To Do', 'In Progress']
        }
        with(GUI.callbacksFor(key)[1]) {
            method == 'POST'
            path == '/api/callbacks/internal'
            header('Authorization') == "Bearer $TOKEN".toString()
            header('X-Beadle-User') == 'system'
            header('X-Correlation-Id') == correlationId
            body == [system: 'CYBERTRACK', key: key, status: 'In Progress', at: (createdAt + STEP).toString()]
        }

        when:
        clock.advance(STEP)

        then: 'the ticket is no longer followed once Done, and every callback is in the call log of its creation'
        conditions.eventually {
            assert GUI.callbacksFor(key)*.body*.status == ['To Do', 'In Progress', 'Done']
            assert jdbc.queryForObject('''SELECT COUNT(*) FROM BEADLE_INTERNAL_CYBERTRACK_TICKET
                    WHERE TICKET_KEY = ? AND CLOSED_AT IS NOT NULL''', Integer, key) == 1
            assert callLog().findAll { it.system == 'GUI' }*.subMap('userName', 'target', 'outcome') == [
                    [userName: 'system', target: "$key To Do".toString(), outcome: 'SUCCESS'],
                    [userName: 'system', target: "$key In Progress".toString(), outcome: 'SUCCESS'],
                    [userName: 'system', target: "$key Done".toString(), outcome: 'SUCCESS']]
        }
    }

    def "a callback Beadle-GUI did not take is sent again on the next poll"() {
        given:
        String key = api.post('/api/internal/cybertrack/tickets', ticket()).json.key
        GUI.refuse(key, 1)

        expect:
        conditions.eventually {
            assert GUI.callbacksFor(key)*.status == [503, 202]
            assert callLog().findAll { it.system == 'GUI' }*.outcome == ['FAILURE', 'SUCCESS']
        }
        GUI.callbacksFor(key)*.body*.status == ['To Do', 'To Do']
    }
}
