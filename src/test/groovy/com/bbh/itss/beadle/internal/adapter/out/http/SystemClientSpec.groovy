package com.bbh.itss.beadle.internal.adapter.out.http

import com.bbh.itss.beadle.internal.domain.shared.RefusedException
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.timestamp
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import static org.springframework.http.HttpStatus.CONFLICT
import static org.springframework.http.HttpStatus.NOT_FOUND

class SystemClientSpec extends Specification {

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()

    def "a system is called under its trimmed URL with the token trimmed, as a secret written from a file ends in a newline"() {
        given:
        server.expect(requestTo('https://jira.bbh.com/rest/api/2/myself'))
                .andExpect(header('Authorization', 'Bearer ' + sent)).andExpect(header('Accept', 'application/json'))
                .andRespond(withSuccess('{"name":"beadle"}', APPLICATION_JSON))
        def jira = new SystemClient('Jira', builder, ' https://jira.bbh.com/ ', token)

        expect:
        jira.call('read myself') { it.get().uri('/rest/api/2/myself').retrieve().body(Map) } == [name: 'beadle']
        jira.url() == 'https://jira.bbh.com'
        server.verify()

        where:
        token              || sent
        'secret-token\r\n' || 'secret-token'
        null               || ''
    }

    def "a status the caller gives a meaning answers what the caller says, or fails with the caller's failure"() {
        given:
        server.expect(requestTo('https://jira.bbh.com/x')).andRespond(withStatus(NOT_FOUND))
        server.expect(requestTo('https://jira.bbh.com/x')).andRespond(withStatus(CONFLICT))
        def jira = new SystemClient('Jira', builder, 'https://jira.bbh.com', 't')
        def answers = [404: { 'none' } as Supplier, 409: SystemClient.thrown { new IllegalStateException('stale') }]

        expect:
        jira.call('read x', answers) { it.get().uri('/x').retrieve().body(String) } == 'none'

        when:
        jira.call('read x', answers) { it.get().uri('/x').retrieve().body(String) }

        then:
        def stale = thrown(IllegalStateException)
        stale.message == 'stale'
    }

    def "a call answered with #answered fails naming the system and the operation"() {
        given:
        server.expect(requestTo('https://jira.bbh.com/x')).andRespond(response)

        when:
        new SystemClient('Jira', builder, 'https://jira.bbh.com', 't').call('read x') { it.get().uri('/x').retrieve().body(String) }

        then:
        def failed = thrown(type)
        failed.message.startsWith(message)
        failed.message.length() <= 'Jira refused to read x: '.length() + SystemClient.MAX_REASON

        where:
        answered                | response                                         || type                 | message
        'a refusal'             | withBadRequest()                                 || RefusedException     | 'Jira refused to read x: 400 Bad Request'
        'a server error'        | withServerError()                                || UncheckedIOException | 'Jira could not read x: 500 Internal Server Error'
        'a reason too long'     | { throw new ResourceAccessException('y' * 400) } || UncheckedIOException | 'Jira could not read x: ' + 'y' * 300
        'a reason without text' | { throw new ResourceAccessException('') }        || UncheckedIOException | 'Jira could not read x: ResourceAccessException'
    }

    def "a timestamp is read in ISO or Atlassian notation, and none stays none"() {
        expect:
        timestamp('2026-10-15T09:00:00Z') == Instant.parse('2026-10-15T09:00:00Z')
        timestamp('2026-10-15T11:00:00.000+0200') == Instant.parse('2026-10-15T09:00:00Z')
        timestamp(null) == null
    }

    def "JQL values are quoted with their quotes escaped and listed in parentheses"() {
        expect:
        Jql.quoted('1 "beta"') == '"1 \\"beta\\""'
        Jql.list(['CERT-1', 'CERT-2']) == '("CERT-1", "CERT-2")'
    }
}
