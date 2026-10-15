package com.bbh.itss.beadle.internal.adapter.out.cybertrack

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.NO_KEY
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.REFUSED
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackAdapter.UNREADABLE
import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.DemoCyberTrackAdapter.STEP
import static com.bbh.itss.beadle.internal.support.Fixtures.IMPLEMENTATION_DATE
import static com.bbh.itss.beadle.internal.support.Fixtures.secureCoding
import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.Clock.fixed
import static java.time.Clock.offset
import static java.time.ZoneOffset.UTC

class CyberTrackAdaptersSpec extends Specification {

    static final SecureCodingTicket TICKET = new SecureCodingTicket('CHG0031001', 'CertScanner', IMPLEMENTATION_DATE,
            secureCoding())
    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    HttpServer jira = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)
    List<Map> requests = new CopyOnWriteArrayList<>()
    int status = 201
    String answer = '{"id":"10001","key":"SCP-1234","self":"https://jira/rest/api/2/issue/10001"}'

    def setup() {
        jira.createContext('/') { HttpExchange exchange ->
            requests << [method       : exchange.requestMethod, path: exchange.requestURI.path,
                         query        : exchange.requestURI.query,
                         authorization: exchange.requestHeaders.getFirst('Authorization'),
                         contentType  : exchange.requestHeaders.getFirst('Content-Type'),
                         body         : exchange.requestMethod == 'POST' ? new JsonSlurper().parse(exchange.requestBody) : null]
            byte[] bytes = answer.getBytes(UTF_8)
            exchange.responseHeaders.add('Content-Type', 'application/json')
            exchange.sendResponseHeaders(status, bytes.length ?: -1)
            exchange.responseBody.withStream { it.write(bytes) }
            exchange.close()
        }
        jira.start()
    }

    def cleanup() {
        jira.stop(0)
    }

    def "the secure coding ticket is created in the configured Jira project with its summary and description"() {
        given:
        def cyberTrack = adapter(new CyberTrackProperties(url + '/', 'secret-token', 'SCP', 'Security Task'))

        when:
        def created = cyberTrack.create(TICKET)

        then:
        cyberTrack.connected()
        created == new CreatedTicket('SCP-1234', url + '/browse/SCP-1234')
        requests.size() == 1
        with(requests[0]) {
            method == 'POST'
            path == '/rest/api/2/issue'
            authorization == 'Bearer secret-token'
            contentType.startsWith('application/json')
            body == [fields: [project    : [key: 'SCP'], issuetype: [name: 'Security Task'],
                              summary    : 'APO-12345_CertScanner-10152026',
                              description: '''\
                                  APO number: APO-12345
                                  Application: CertScanner
                                  Implementation date: 10152026
                                  Bitbucket URL (SAST scan): https://bitbucket.bbh.com/projects/CERT/repos/cert
                                  Artifact link (OSA - Nexus IQ scan): https://jenkins.bbh.com/job/CERT/job/cert-release/
                                  QC application link (DAST scan): https://cert.qc.bbh.com
                                  ProTech change: CHG0031001'''.stripIndent()]]
        }
    }

    def "a Jira answer of #problem is refused with the reason"() {
        given:
        this.status = status
        this.answer = answer

        when:
        adapter(new CyberTrackProperties(url, null, 'SCP', 'Task')).create(TICKET)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith(REFUSED)
        refused.message.contains(reason)
        refused.message.length() <= REFUSED.length() + 300

        where:
        problem               | status | answer                                          || reason
        'a refusal'           | 400    | '{"errors":{"summary":"Summary is required."}}' || '400 Bad Request'
        'a missing key'       | 201    | '{"id":"10001"}'                                || NO_KEY
        'an empty key'        | 201    | '{"key":" "}'                                   || NO_KEY
        'a long server error' | 500    | '{"errorMessages":["' + 'x' * 600 + '"]}'       || '500 Internal Server Error'
    }

    def "an unreachable Jira is refused with the reason"() {
        given:
        def cyberTrack = adapter(new CyberTrackProperties('http://127.0.0.1:1', 'token', 'SCP', 'Task'))

        when:
        cyberTrack.create(TICKET)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith(REFUSED)
    }

    def "a ticket is read with its status and the time of its last change in #zone"() {
        given:
        status = 200
        answer = '{"key":"SCP-1234","fields":{"status":{"name":"In Progress"},"updated":"' + updated + '"}}'

        when:
        def ticket = adapter(new CyberTrackProperties(url, 'secret-token', 'SCP', 'Task')).read('SCP-1234')

        then:
        ticket == new CyberTrackTicket('SCP-1234', 'In Progress', url + '/browse/SCP-1234', NOW)
        requests.size() == 1
        with(requests[0]) {
            method == 'GET'
            path == '/rest/api/2/issue/SCP-1234'
            query == 'fields=status,updated'
            authorization == 'Bearer secret-token'
        }

        where:
        zone              | updated
        'Jira notation'   | '2026-10-15T11:00:00.000+0200'
        'ISO notation'    | '2026-10-15T09:00:00Z'
    }

    def "an unknown ticket is not found and a Jira answer of #problem is refused when reading"() {
        given:
        this.status = status
        this.answer = answer
        def cyberTrack = adapter(new CyberTrackProperties(url, 'token', 'SCP', 'Task'))

        when:
        cyberTrack.read('SCP-9')

        then:
        def refused = thrown(exception)
        refused.message.contains(reason)

        where:
        problem           | status | answer                               || exception              | reason
        'not found'       | 404    | '{"errorMessages":["Issue Does Not Exist"]}' || NoSuchElementException | 'CyberTrack ticket SCP-9 does not exist'
        'a server error'  | 500    | '{"errorMessages":["boom"]}'         || UncheckedIOException   | UNREADABLE + '500'
        'no fields'       | 200    | '{"key":"SCP-9"}'                    || UncheckedIOException   | UNREADABLE + 'Jira answered without the fields of SCP-9'
    }

    def "the demo numbers tickets in its project one after another, moves them on every two minutes and says it is not connected"() {
        given:
        def demo = new DemoCyberTrackAdapter(fixed(NOW, UTC), new CyberTrackProperties(null, null, 'SCP', 'Task'))

        when:
        def first = demo.create(TICKET)
        def second = demo.create(TICKET)

        then:
        !demo.connected()
        first.key() ==~ /SCP-\d{4}/
        first.url() == null
        (second.key() - 'SCP-') as int == (first.key() - 'SCP-') as int + 1
        demo.read(first.key()) == new CyberTrackTicket(first.key(), 'To Do', null, NOW)
        requests == []

        expect:
        new DemoCyberTrackAdapter(offset(fixed(NOW, UTC), elapsed), new CyberTrackProperties(null, null, 'SCP', 'Task'))
                .with { it.created.put(first.key(), NOW); it.read(first.key()) } ==
                new CyberTrackTicket(first.key(), expected, null, NOW + STEP.multipliedBy(steps))

        where:
        elapsed                      || expected      | steps
        STEP.minusMillis(1)          || 'To Do'       | 0
        STEP                         || 'In Progress' | 1
        STEP.multipliedBy(2).minusMillis(1) || 'In Progress' | 1
        STEP.multipliedBy(2)         || 'Done'        | 2
        STEP.multipliedBy(100)       || 'Done'        | 2
    }

    def "the demo adopts a ticket of its project it never created as done, and knows no other key"() {
        given:
        def demo = new DemoCyberTrackAdapter(fixed(NOW, UTC), new CyberTrackProperties(null, null, 'SCP', 'Task'))

        when:
        def adopted = demo.read('SCP-77')

        then:
        adopted == new CyberTrackTicket('SCP-77', 'Done', null, NOW)
        demo.read('SCP-77') == adopted

        when:
        demo.read('CERT-77')

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'CyberTrack ticket CERT-77 does not exist'
    }

    def "Jira is used only once its URL is set, with SCP and Task by default"() {
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration))
                .withUserConfiguration(Adapters)
                .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBean(CyberTrackPort).class == type
            assert context.getBean(CyberTrackProperties).projectKey() == 'SCP'
            assert context.getBean(CyberTrackProperties).issueType() == 'Task'
        }

        where:
        properties                                           || type
        []                                                   || DemoCyberTrackAdapter
        ['beadle.cybertrack.url=']                           || DemoCyberTrackAdapter
        ['beadle.cybertrack.url=https://cybertrack.bbh.com'] || CyberTrackAdapter
    }

    private String getUrl() {
        "http://127.0.0.1:${jira.address.port}"
    }

    private static CyberTrackAdapter adapter(CyberTrackProperties properties) {
        new CyberTrackAdapter(properties, RestClient.builder())
    }

    @EnableConfigurationProperties(CyberTrackProperties)
    @Import([DemoCyberTrackAdapter, CyberTrackAdapter])
    static class Adapters {
    }
}
