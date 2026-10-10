package com.bbh.itss.dso.portal.adapter.out.jira

import com.bbh.itss.dso.portal.application.change.port.out.CyberTrackPort
import com.bbh.itss.dso.portal.domain.change.SecureCodingTicket
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

import java.util.concurrent.CopyOnWriteArrayList

import static com.bbh.itss.dso.portal.adapter.out.jira.JiraCyberTrackAdapter.NO_KEY
import static com.bbh.itss.dso.portal.adapter.out.jira.JiraCyberTrackAdapter.REFUSED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCoding
import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8

class CyberTrackAdaptersSpec extends Specification {

    static final SecureCodingTicket TICKET = new SecureCodingTicket('CHG0031001', 'CertScanner', '10152026',
            secureCoding())

    HttpServer jira = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)
    List<Map> requests = new CopyOnWriteArrayList<>()
    int status = 201
    String answer = '{"id":"10001","key":"SCP-1234","self":"https://jira/rest/api/2/issue/10001"}'

    def setup() {
        jira.createContext('/') { HttpExchange exchange ->
            requests << [method       : exchange.requestMethod, path: exchange.requestURI.path,
                         authorization: exchange.requestHeaders.getFirst('Authorization'),
                         contentType  : exchange.requestHeaders.getFirst('Content-Type'),
                         body         : new JsonSlurper().parse(exchange.requestBody)]
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
        def cyberTrack = adapter(new CyberTrackProperties(url, 'secret-token', 'SCP', 'Security Task'))

        when:
        def key = cyberTrack.create(TICKET)

        then:
        cyberTrack.connected()
        key == 'SCP-1234'
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

    def "the demo numbers tickets in its project one after another and says it is not connected"() {
        given:
        def demo = new DemoCyberTrackAdapter(new CyberTrackProperties(null, null, 'SCP', 'Task'))

        when:
        def first = demo.create(TICKET)
        def second = demo.create(TICKET)

        then:
        !demo.connected()
        first ==~ /SCP-\d{4}/
        (second - 'SCP-') as int == (first - 'SCP-') as int + 1
        requests == []
    }

    def "Jira is used only once its URL is set, with SCP and Task by default"() {
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration))
                .withUserConfiguration(Adapters)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBean(CyberTrackPort).class == type
            assert context.getBean(CyberTrackProperties).projectKey() == 'SCP'
            assert context.getBean(CyberTrackProperties).issueType() == 'Task'
        }

        where:
        properties                                        || type
        []                                                || DemoCyberTrackAdapter
        ['dso.cybertrack.url=']                           || DemoCyberTrackAdapter
        ['dso.cybertrack.url=https://cybertrack.bbh.com'] || JiraCyberTrackAdapter
    }

    private String getUrl() {
        "http://127.0.0.1:${jira.address.port}"
    }

    private static JiraCyberTrackAdapter adapter(CyberTrackProperties properties) {
        new JiraCyberTrackAdapter(properties, RestClient.builder())
    }

    @EnableConfigurationProperties(CyberTrackProperties)
    @Import([DemoCyberTrackAdapter, JiraCyberTrackAdapter])
    static class Adapters {
    }
}
