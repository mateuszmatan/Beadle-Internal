package com.bbh.itss.beadle.internal.adapter.out.http

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.MAX_REASON
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp
import static org.springframework.http.HttpStatus.BAD_GATEWAY
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class ExternalSystemsSpec extends Specification {

    def "a client calls the system under its base URL, without a trailing slash, with the bearer token"() {
        given:
        def builder = RestClient.builder()
        def server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo('https://jira.bbh.com/rest/api/2/myself'))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andRespond(withSuccess('{"name":"beadle"}', APPLICATION_JSON))

        when:
        def answer = client(builder, ' https://jira.bbh.com/ ', 'secret-token').get().uri('/rest/api/2/myself')
                .retrieve().body(Map)

        then:
        answer == [name: 'beadle']
        server.verify()
    }

    def "a missing token is sent as an empty bearer so the system refuses the call itself"() {
        given:
        def builder = RestClient.builder()
        def server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo('https://qtest.bbh.com/api/v3/projects')).andExpect(header('Authorization', 'Bearer '))
                .andRespond(withSuccess('[]', APPLICATION_JSON))

        expect:
        client(builder, 'https://qtest.bbh.com', null).get().uri('/api/v3/projects').retrieve().body(List) == []
    }

    def "a refusal names the system and the operation and keeps at most 300 characters of the reason"() {
        when:
        def refused = refusal('Jira', 'read the epics of CERT 1.4', cause)

        then:
        refused.message == 'Jira could not read the epics of CERT 1.4: ' + reason
        refused.message.length() <= 'Jira could not read the epics of CERT 1.4: '.length() + MAX_REASON
        refused.cause.cause.is(cause)

        where:
        cause                                                                                    || reason
        new ResourceAccessException('I/O error on GET request: Connection refused')             || 'I/O error on GET request: Connection refused'
        new HttpClientErrorException(BAD_GATEWAY, 'Bad Gateway')                                 || '502 Bad Gateway'
        new ResourceAccessException('x' * 400)                                                   || 'x' * 300
        new ResourceAccessException('')                                                          || 'ResourceAccessException'
    }

    def "a timestamp is read in ISO or Atlassian notation, and none stays none"() {
        expect:
        timestamp('2026-10-15T09:00:00Z') == Instant.parse('2026-10-15T09:00:00Z')
        timestamp('2026-10-15T11:00:00.000+0200') == Instant.parse('2026-10-15T09:00:00Z')
        timestamp('2026-10-15T09:00:00.123+00:00') == Instant.parse('2026-10-15T09:00:00.123Z')
        timestamp(null) == null
    }

    def "the HTTP clients of Beadle-Internal connect within 5 seconds and read within 30"() {
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration))
                .withPropertyValues('spring.http.clients.connect-timeout=5s', 'spring.http.clients.read-timeout=30s')

        expect:
        runner.run { context ->
            def settings = context.getBean(HttpClientSettings)
            assert settings.connectTimeout() == Duration.ofSeconds(5)
            assert settings.readTimeout() == Duration.ofSeconds(30)
        }
    }
}
