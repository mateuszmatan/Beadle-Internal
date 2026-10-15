package com.bbh.itss.beadle.internal.adapter.out.qtest

import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.internal.support.Fixtures.cycle
import static com.bbh.itss.beadle.internal.support.Fixtures.project
import static com.bbh.itss.beadle.internal.support.Fixtures.run
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest

class QTestAdapterSpec extends Specification {

    static final String URL = 'https://qtest.bbh.com'
    static final String PROJECT = URL + '/api/v3/projects/1'
    static final String RUNS = PROJECT + '/test-runs?parentId=10&parentType=test-cycle&expand=descendants&page='

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def qtest = new QTestAdapter(new QTestProperties(URL + '/', 'secret-token'), builder)

    def "the projects are listed with their ids and names"() {
        given:
        server.expect(requestTo(URL + '/api/v3/projects')).andExpect(method(GET))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andRespond(withSuccess('[{"id":1,"name":"CertScanner","description":""},{"id":2,"name":"PayHub"}]',
                        APPLICATION_JSON))

        expect:
        qtest.connected()
        qtest.projects() == [project(), project(id: 2L, name: 'PayHub')]
    }

    def "the test cycles of a project are listed flat, children after their parent"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles')).andExpect(method(GET))
                .andRespond(withSuccess('''[
                    {"id":10,"name":"Release 1.4","pid":"CL-10","test-cycles":[{"id":11,"name":"Sprint 1","pid":"CL-11"},
                     {"id":12,"name":"Sprint 2","pid":"CL-12","test-cycles":[]}]},
                    {"id":20,"name":"Regression","pid":"CL-20"}]''', APPLICATION_JSON))

        expect:
        qtest.cycles(1) == [cycle(), cycle(id: 11L, name: 'Sprint 1', pid: 'CL-11'),
                            cycle(id: 12L, name: 'Sprint 2', pid: 'CL-12'), cycle(id: 20L, name: 'Regression', pid: 'CL-20')]
    }

    def "the test runs of a cycle come page by page with the status and time of their last result"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10,"name":"Release 1.4","pid":"CL-10"}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100')).andExpect(method(GET))
                .andRespond(withSuccess('{"page":1,"pageSize":1,"total":2,"items":[{"id":100,"name":"Sign in with SSO","pid":"TR-100"}]}',
                        APPLICATION_JSON))
        server.expect(requestTo(RUNS + '2&pageSize=100'))
                .andRespond(withSuccess('{"page":2,"pageSize":1,"total":2,"items":[{"id":101,"name":"Sign out","pid":"TR-101"}]}',
                        APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run'))
                .andRespond(withSuccess('{"id":5000,"status":{"id":601,"name":"Passed"},"execution_date":"2026-10-15T09:00:00+00:00"}',
                        APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/101/test-logs/last-run')).andRespond(withResourceNotFound())

        when:
        def runs = qtest.runs(1, 10)

        then:
        runs == [run(), run(id: 101L, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null)]
        server.verify()
    }

    def "the summary counts the runs of a cycle by status"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100'))
                .andRespond(withSuccess('{"page":1,"pageSize":100,"total":3,"items":[{"id":100,"name":"a","pid":"TR-100"},' +
                        '{"id":101,"name":"b","pid":"TR-101"},{"id":102,"name":"c","pid":"TR-102"}]}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Passed"},"execution_date":"2026-10-15T09:00:00+00:00"}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/101/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Failed"},"execution_date":"2026-10-15T10:00:00+00:00"}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/102/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Blocked"},"execution_date":null}', APPLICATION_JSON))

        expect:
        qtest.summary(1, 10) == new QTestSummary(3, 1, 1, 1, 0)
    }

    def "an unknown project or cycle is not found"() {
        given:
        server.expect(requestTo(URL + '/api/v3/projects/9/test-cycles')).andRespond(withResourceNotFound())
        server.expect(requestTo(PROJECT + '/test-cycles/99')).andRespond(withResourceNotFound())

        when:
        qtest.cycles(9)

        then:
        def project = thrown(NoSuchElementException)
        project.message == 'qTest project 9 does not exist'

        when:
        qtest.runs(1, 99)

        then:
        def cycle = thrown(NoSuchElementException)
        cycle.message == 'qTest test cycle 99 does not exist'
    }

    def "a qTest refusal to #operation is reported with its reason"() {
        given:
        server.expect(requestTo(url)).andRespond(response)

        when:
        call(qtest)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('qTest could not ' + operation + ': ' + reason)

        where:
        operation                           | url                      | response                  | call                                   || reason
        'list the projects'                 | URL + '/api/v3/projects' | withUnauthorizedRequest() | ({ QTestAdapter it -> it.projects() }) || '401 Unauthorized'
        'list the test cycles of project 1' | PROJECT + '/test-cycles' | withServerError()         | ({ QTestAdapter it -> it.cycles(1) })  || '500 Internal Server Error'
        'read the test cycle 11'            | PROJECT + '/test-cycles/11' | withServerError()      | ({ QTestAdapter it -> it.runs(1, 11) }) || '500 Internal Server Error'
    }

    def "a refusal to read the last result of a test run is reported with its reason"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100'))
                .andRespond(withSuccess('{"page":1,"pageSize":100,"total":1,"items":[{"id":100,"name":"a","pid":"TR-100"}]}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run')).andRespond(withServerError())

        when:
        qtest.summary(1, 10)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('qTest could not read the last result of test run 100: 500')
    }

    def "a refusal to list the test runs is reported with its reason"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100')).andRespond(withServerError())

        when:
        qtest.runs(1, 10)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('qTest could not list the test runs of test cycle 10: 500')
    }
}
