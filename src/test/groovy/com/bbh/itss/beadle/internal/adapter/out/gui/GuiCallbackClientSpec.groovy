package com.bbh.itss.beadle.internal.adapter.out.gui

import com.bbh.itss.beadle.internal.adapter.ServiceTokenProperties
import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import groovy.json.JsonSlurper
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.support.Fixtures.statusCallback
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpStatus.ACCEPTED
import static org.springframework.http.HttpStatus.FOUND
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest

class GuiCallbackClientSpec extends Specification {

    static final String URL = 'http://beadle:8081'

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def client = new GuiCallbackClient(new GuiProperties(URL + '/'), new ServiceTokenProperties('service-token\n'), builder)

    def "a status change is posted with the service token, the system as caller and the correlation id in the header only"() {
        given:
        server.expect(requestTo(URL + '/api/callbacks/internal')).andExpect(method(POST))
                .andExpect(header('Authorization', 'Bearer service-token'))
                .andExpect(header('X-Beadle-User', 'system'))
                .andExpect(header('X-Correlation-Id', 'corr-1'))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect { ClientHttpRequest request ->
                    assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) ==
                            [system: 'CYBERTRACK', key: 'SCP-1234', status: 'In Progress', at: '2026-10-15T09:00:00Z']
                }
                .andRespond(withStatus(ACCEPTED))
        server.expect(requestTo(URL + '/api/callbacks/internal')).andExpect(headerDoesNotExist('X-Correlation-Id'))
                .andRespond(withStatus(ACCEPTED))

        when:
        CallerContext.run(new Caller('system', 'corr-1')) { client.notify(statusCallback()) }
        client.notify(statusCallback())

        then:
        server.verify()
    }

    def "a Beadle-GUI answering #problem refuses the callback with the reason"() {
        given:
        server.expect(requestTo(URL + '/api/callbacks/internal')).andRespond(response)

        when:
        client.notify(statusCallback())

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('Beadle-GUI could not take the callback for SCP-1234: ' + reason)

        where:
        problem          | response                                                   || reason
        'a server error' | withServerError()                                          || '500 Internal Server Error'
        'a refusal'      | withUnauthorizedRequest()                                  || '401 Unauthorized'
        'a redirect'     | withStatus(FOUND).header('Location', URL + '/oauth2/ping') || 'Beadle-GUI answered 302 FOUND'
    }
}
