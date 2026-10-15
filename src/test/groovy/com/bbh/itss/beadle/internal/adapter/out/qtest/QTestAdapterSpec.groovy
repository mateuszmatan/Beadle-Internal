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
    static final String ROOT_CYCLES = PROJECT + '/test-cycles?parentId=0&parentType=root&expand=descendants'
    static final String RELEASES = PROJECT + '/releases'

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

    def "the test cycles of a project are listed flat, those of the root then those of each release, children after their parent"() {
        given: 'the cycles under the root, with their descendants'
        server.expect(requestTo(ROOT_CYCLES)).andExpect(method(GET))
                .andExpect(header('Authorization', 'Bearer secret-token'))
                .andRespond(withSuccess('''[
                    {"id":10,"name":"Release 1.4","pid":"CL-10","test-cycles":[{"id":11,"name":"Sprint 1","pid":"CL-11",
                     "test-suites":[{"id":900,"name":"Smoke","pid":"TS-900"}]},
                     {"id":12,"name":"Sprint 2","pid":"CL-12","test-cycles":[{"id":13,"name":"Hotfix","pid":"CL-13"}]}]},
                    {"id":20,"name":"Regression","pid":"CL-20"}]''', APPLICATION_JSON))

        and: 'the releases, and the cycles under each of them'
        server.expect(requestTo(RELEASES)).andExpect(method(GET))
                .andRespond(withSuccess('[{"id":5,"name":"CertScanner 1.4","pid":"RL-5"},{"id":6,"name":"CertScanner 1.5","pid":"RL-6"},' +
                        '{"id":7,"name":"Deleted since","pid":"RL-7"}]', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-cycles?parentId=5&parentType=release&expand=descendants'))
                .andRespond(withSuccess('[{"id":30,"name":"SIT","pid":"CL-30","test-cycles":[{"id":31,"name":"SIT rerun","pid":"CL-31"}]}]',
                        APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-cycles?parentId=6&parentType=release&expand=descendants'))
                .andRespond(withSuccess('[]', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-cycles?parentId=7&parentType=release&expand=descendants'))
                .andRespond(withResourceNotFound())

        expect:
        qtest.cycles(1) == [cycle(), cycle(id: 11L, name: 'Sprint 1', pid: 'CL-11'),
                            cycle(id: 12L, name: 'Sprint 2', pid: 'CL-12'), cycle(id: 13L, name: 'Hotfix', pid: 'CL-13'),
                            cycle(id: 20L, name: 'Regression', pid: 'CL-20'),
                            cycle(id: 30L, name: 'SIT', pid: 'CL-30'), cycle(id: 31L, name: 'SIT rerun', pid: 'CL-31')]
        server.verify()
    }

    def "the test runs of a cycle come page by page with the status and time of their last result"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10,"name":"Release 1.4","pid":"CL-10"}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100')).andExpect(method(GET))
                .andRespond(withSuccess('{"page":1,"page_size":1,"total":2,"items":[{"id":100,"name":"Sign in with SSO","pid":"TR-100"}]}',
                        APPLICATION_JSON))
        server.expect(requestTo(RUNS + '2&pageSize=100'))
                .andRespond(withSuccess('{"page":2,"page_size":1,"total":2,"items":[{"id":101,"name":"Sign out","pid":"TR-101"}]}',
                        APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run'))
                .andRespond(withSuccess('{"id":5000,"status":{"id":601,"name":"Passed"},' +
                        '"exe_start_date":"2026-10-15T10:45:00+02:00","exe_end_date":"2026-10-15T11:00:00+02:00"}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/101/test-logs/last-run')).andRespond(withResourceNotFound())

        when:
        def runs = qtest.runs(1, 10)

        then:
        runs == [run(), run(id: 101L, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null)]
        server.verify()
    }

    def "a run executed #executed reads as executed at #executedAt"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100'))
                .andRespond(withSuccess('{"items":[{"id":100,"name":"Sign in with SSO","pid":"TR-100"}]}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"id":602,"name":"Failed"}' + dates + '}', APPLICATION_JSON))

        expect:
        qtest.runs(1, 10) == [run(status: 'Failed', executedAt: executedAt ? Instant.parse(executedAt) : null)]

        where:
        executed                     | dates                                                                                   || executedAt
        'with an end'                | ',"exe_start_date":"2026-10-15T08:00:00Z","exe_end_date":"2026-10-15T09:00:00.000Z"'   || '2026-10-15T09:00:00Z'
        'without an end'             | ',"exe_start_date":"2026-10-15T15:00:00+07:00","exe_end_date":null'                    || '2026-10-15T08:00:00Z'
        'with a blank end'           | ',"exe_start_date":"2026-10-15T08:00:00Z","exe_end_date":""'                           || '2026-10-15T08:00:00Z'
        'without a time'             | ''                                                                                      || null
        'in an unknown notation'     | ',"exe_end_date":"15/10/2026 09:00"'                                                    || null
    }

    def "a refusal while listing the releases or their cycles is reported with what was refused"() {
        given:
        server.expect(requestTo(ROOT_CYCLES)).andRespond(withSuccess('[]', APPLICATION_JSON))
        server.expect(requestTo(RELEASES)).andRespond(releases)
        if (cycles) {
            server.expect(requestTo(PROJECT + '/test-cycles?parentId=5&parentType=release&expand=descendants')).andRespond(cycles)
        }

        when:
        qtest.cycles(1)

        then:
        def refused = thrown(exception)
        refused.message.startsWith(message)

        where:
        releases                                                   | cycles            || exception              | message
        withServerError()                                          | null              || UncheckedIOException   | 'qTest could not list the releases of project 1: 500'
        withResourceNotFound()                                     | null              || NoSuchElementException | 'qTest project 1 does not exist'
        withSuccess('[{"id":5,"name":"1.4"}]', APPLICATION_JSON)    | withServerError() || UncheckedIOException   | 'qTest could not list the test cycles of release 5: 500'
    }

    def "the summary counts the runs of a cycle by status"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=100'))
                .andRespond(withSuccess('{"page":1,"page_size":100,"total":3,"items":[{"id":100,"name":"a","pid":"TR-100"},' +
                        '{"id":101,"name":"b","pid":"TR-101"},{"id":102,"name":"c","pid":"TR-102"}]}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Passed"},"exe_end_date":"2026-10-15T09:00:00+00:00"}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/101/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Failed"},"exe_end_date":"2026-10-15T10:00:00+00:00"}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/102/test-logs/last-run'))
                .andRespond(withSuccess('{"status":{"name":"Blocked"},"exe_end_date":null}', APPLICATION_JSON))

        expect:
        qtest.summary(1, 10) == new QTestSummary(3, 1, 1, 1, 0)
    }

    def "an unknown project or cycle is not found"() {
        given:
        server.expect(requestTo(URL + '/api/v3/projects/9/test-cycles?parentId=0&parentType=root&expand=descendants'))
                .andRespond(withResourceNotFound())
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
        'list the test cycles of project 1' | ROOT_CYCLES              | withServerError()         | ({ QTestAdapter it -> it.cycles(1) })  || '500 Internal Server Error'
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
