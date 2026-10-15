package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.status.port.in.StatusUseCase
import com.bbh.itss.beadle.internal.domain.status.ServiceStatus
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import static com.bbh.itss.beadle.internal.domain.status.SystemStatus.of
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class StatusControllerSpec extends ControllerSpecification {

    StatusUseCase status = Stub()

    @Override
    Object controller() {
        new StatusController(status)
    }

    def "the status names every system with its connection and mode"() {
        given:
        status.status() >> new ServiceStatus(of(false), of(true), of(false), of(true))

        when:
        def response = call(get('/api/internal/status'))

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        bodyOf(response) == [jira      : [connected: false, mode: 'demo'], cyberTrack: [connected: true, mode: 'live'],
                             confluence: [connected: false, mode: 'demo'], qtest: [connected: true, mode: 'live']]
    }

    def "without the service token the status is 401 (#authorization)"() {
        when:
        def response = callWithout(get('/api/internal/status'), authorization)

        then:
        response.status == 401
        response.contentType.startsWith('application/problem+json')
        response.getHeader('WWW-Authenticate') == 'Bearer'
        bodyOf(response).title == 'Unauthorized'

        where:
        authorization << unauthorized()
    }
}
