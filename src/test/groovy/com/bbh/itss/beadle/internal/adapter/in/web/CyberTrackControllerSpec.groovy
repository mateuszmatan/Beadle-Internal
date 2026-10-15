package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import static com.bbh.itss.beadle.internal.support.Fixtures.cyberTrackTicket
import static com.bbh.itss.beadle.internal.support.Fixtures.secureCodingJson
import static com.bbh.itss.beadle.internal.support.Fixtures.ticket
import static com.bbh.itss.beadle.internal.support.Fixtures.ticketJson
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class CyberTrackControllerSpec extends ControllerSpecification {

    CyberTrackUseCase cyberTrack = Mock()

    @Override
    Object controller() {
        new CyberTrackController(cyberTrack)
    }

    def "a secure coding ticket is created from the request and answered with its key and link"() {
        when:
        def response = call(json(post('/api/internal/cybertrack/tickets'), ticketJson(changeNumber: ' CHG0031001 ')))

        then:
        1 * cyberTrack.create(ticket()) >> new CreatedTicket('SCP-1234', url)
        response.status == 201
        response.getHeader('Location') == 'http://localhost/api/internal/cybertrack/tickets/SCP-1234'
        bodyOf(response) == [key: 'SCP-1234', url: url]

        where:
        url << ['https://cybertrack.bbh.com/browse/SCP-1234', null]
    }

    def "a ticket is read with its status and the time of its last change"() {
        when:
        def response = call(get('/api/internal/cybertrack/tickets/SCP-1234'))

        then:
        1 * cyberTrack.read('SCP-1234') >> cyberTrackTicket()
        response.status == 200
        bodyOf(response) == [key: 'SCP-1234', status: 'To Do', url: 'https://cybertrack.bbh.com/browse/SCP-1234',
                             updated: '2026-10-15T09:00:00Z']
    }

    def "a ticket request with #problem is refused naming the fields"() {
        when:
        def response = call(json(post('/api/internal/cybertrack/tickets'), body))

        then:
        0 * cyberTrack._
        response.status == 400
        response.contentType.startsWith('application/problem+json')
        with(bodyOf(response)) {
            title == 'Validation failed'
            problems*.field == fields
            problems*.message == messages
        }

        where:
        problem                  | body                                                          || fields                                 | messages
        'no change number'       | ticketJson(changeNumber: ' ')                                 || ['changeNumber']                       | ['is required']
        'no date'                | ticketJson(implementationDate: null)                          || ['implementationDate']                 | ['is required']
        'no inputs'              | ticketJson(inputs: null)                                      || ['inputs.apoNumber', 'inputs.bitbucketUrl', 'inputs.artifactLink', 'inputs.qcApplicationLink'] | ['is required'] * 4
        'a bad link'             | ticketJson(inputs: secureCodingJson() + [bitbucketUrl: 'ftp://x']) || ['inputs.bitbucketUrl']          | ['must be a link starting with https:// or http://']
    }

    def "a ticket request with #problem is malformed"() {
        when:
        def response = call(post('/api/internal/cybertrack/tickets').contentType('application/json').content(body))

        then:
        0 * cyberTrack._
        response.status == 400
        with(bodyOf(response)) {
            title == 'Malformed request'
            it.detail == detail
        }

        where:
        problem       | body                                                  || detail
        'a bad date'  | '{"changeNumber":"CHG1","implementationDate":"20-10-2026"}' || 'implementationDate has a value this field cannot hold'
        'no body'     | ''                                                    || 'The request body is missing.'
    }

    def "an unknown ticket is 404 and a refusing CyberTrack is 502"() {
        when:
        def missing = call(get('/api/internal/cybertrack/tickets/SCP-9'))
        def refused = call(json(post('/api/internal/cybertrack/tickets'), ticketJson()))

        then:
        1 * cyberTrack.read('SCP-9') >> { throw new NoSuchElementException('CyberTrack ticket SCP-9 does not exist') }
        1 * cyberTrack.create(_) >> { throw new UncheckedIOException('CyberTrack could not create the secure coding ticket: 400 Bad Request', new IOException()) }
        missing.status == 404
        bodyOf(missing).detail == 'CyberTrack ticket SCP-9 does not exist'
        refused.status == 502
        bodyOf(refused).detail == 'CyberTrack could not create the secure coding ticket: 400 Bad Request'
    }

    def "without the service token #request is 401 (#authorization)"() {
        when:
        def response = callWithout(builder(), authorization)

        then:
        response.status == 401
        bodyOf(response).title == 'Unauthorized'
        0 * cyberTrack._

        where:
        [request, builder, authorization] << [[['creating', { json(post('/api/internal/cybertrack/tickets'), ticketJson()) }],
                                               ['reading', { get('/api/internal/cybertrack/tickets/SCP-1') }]],
                                              unauthorized()].combinations().collect { it[0] + [it[1]] }
    }
}
