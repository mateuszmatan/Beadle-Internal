package com.bbh.itss.beadle.internal.adapter.out.qtest

import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import groovy.json.JsonOutput
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.web.client.RestClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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

    def calls = new ConcurrentLinkedQueue<String>()
    def inFlight = new AtomicInteger()
    def mostInFlight = new AtomicInteger()
    def builder = RestClient.builder().requestInterceptor({ request, body, execution ->
        calls << request.URI.toString()
        execution.execute(request, body)
    } as ClientHttpRequestInterceptor)
    def server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
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
        server.expect(requestTo(RUNS + '1&pageSize=999')).andExpect(method(GET))
                .andRespond(withSuccess('{"page":1,"page_size":1,"total":2,"items":[{"id":100,"name":"Sign in with SSO","pid":"TR-100"}]}',
                        APPLICATION_JSON))
        server.expect(requestTo(RUNS + '2&pageSize=999'))
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

    def "only the last results of executed runs, or of runs without a status, are read, all of them under way at once"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=999')).andRespond(withSuccess(runPage([
                runItem(100, 'Passed', 'Sign in with SSO'), runItem(101, null, 'Sign out'), runItem(102, 'Unexecuted'),
                runItem(103, 'Failed')], 4), APPLICATION_JSON))
        server.expect(requestTo(log(100))).andRespond(slow(100, '{"status":{"id":601,"name":"Passed"},"exe_end_date":"2026-10-15T09:00:00Z"}'))
        server.expect(requestTo(log(101))).andRespond(withResourceNotFound())
        server.expect(requestTo(log(103))).andRespond(slow(100, '{"status":{"id":604,"name":"Blocked"},"exe_end_date":"2026-10-15T10:00:00Z"}'))

        when:
        def runs = qtest.runs(1, 10)

        then: 'the status of the last result wins over the one of the list'
        runs == [run(), run(id: 101L, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null),
                 run(id: 102L, name: 'Run 102', pid: 'TR-102', status: 'Unexecuted', executedAt: null),
                 run(id: 103L, name: 'Run 103', pid: 'TR-103', status: 'Blocked', executedAt: Instant.parse('2026-10-15T10:00:00Z'))]
        calls.size() == 5
        calls.findAll { it.endsWith('/last-run') }.toSet() == [log(100), log(101), log(103)] as Set
        mostInFlight.get() == 2
        server.verify()
    }

    def "a summary of #count runs with their status takes #expected calls, without reading a result"() {
        given:
        def statuses = ['Passed', 'Failed', 'Blocked', 'Unexecuted', 'Incomplete']
        def items = (1..count).collect { runItem(it, statuses[it % statuses.size()]) }
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        items.collate(QTestAdapter.PAGE_SIZE).eachWithIndex { List page, int index ->
            server.expect(requestTo(RUNS + (index + 1) + '&pageSize=999')).andRespond(withSuccess(runPage(page, count), APPLICATION_JSON))
        }

        when:
        def summary = qtest.summary(1, 10)

        then:
        summary == new QTestSummary(count, count.intdiv(5), count.intdiv(5), count.intdiv(5), count - 3 * count.intdiv(5))
        calls.size() == expected
        calls.every { !it.endsWith('/last-run') }
        server.verify()

        where:
        count | expected
        250   | 2
        1_500 | 3
    }

    def "the last results of #count runs without a status take one call each, at most #concurrency under way at once"() {
        given:
        def bounded = new QTestAdapter(new QTestProperties(URL, 'secret-token', concurrency, Duration.ofSeconds(20)), builder)
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=999'))
                .andRespond(withSuccess(runPage((1..count).collect { runItem(it, null) }, count), APPLICATION_JSON))
        (1..count).each {
            server.expect(requestTo(log(it))).andRespond(slow(20, '{"status":{"name":"' + (it % 2 ? 'Passed' : 'Failed') + '"}}'))
        }

        when:
        def summary = bounded.summary(1, 10)

        then:
        summary == new QTestSummary(count, count.intdiv(2), count.intdiv(2), 0, 0)
        calls.size() == 2 + count
        mostInFlight.get() > 1
        mostInFlight.get() <= concurrency
        server.verify()

        where:
        count | concurrency
        40    | 4
    }

    def "a listing slower than the budget is refused within it, and its reads still under way are interrupted"() {
        given:
        def bounded = new QTestAdapter(new QTestProperties(URL, 'secret-token', 1, Duration.ofMillis(300)), builder)
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=999'))
                .andRespond(withSuccess(runPage((1..10).collect { runItem(it, null) }, 10), APPLICATION_JSON))
        (1..10).each { server.expect(requestTo(log(it))).andRespond(slow(200, '{"status":{"name":"Passed"}}')) }
        long started = System.nanoTime()

        when:
        bounded.summary(1, 10)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message == 'qTest could not count the test runs of test cycle 10 within 300 ms: it answers too slowly or the listing is too large'
        Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(1_500)
        new PollingConditions(timeout: 5).eventually {
            assert inFlight.get() == 0
        }
        calls.count { it.endsWith('/last-run') } < 5
    }

    def "a run executed #executed reads as executed at #executedAt"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=999'))
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
        server.expect(requestTo(RUNS + '1&pageSize=999'))
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
        server.expect(requestTo(RUNS + '1&pageSize=999'))
                .andRespond(withSuccess('{"page":1,"pageSize":100,"total":1,"items":[{"id":100,"name":"a","pid":"TR-100"}]}', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-runs/100/test-logs/last-run')).andRespond(withServerError())

        when:
        qtest.summary(1, 10)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('qTest could not read the last result of test run 100: 500')
    }

    def "the cycles of the releases are read at once and listed in the order of the releases"() {
        given:
        server.expect(requestTo(ROOT_CYCLES)).andRespond(withSuccess('[]', APPLICATION_JSON))
        server.expect(requestTo(RELEASES)).andRespond(withSuccess('[{"id":5},{"id":6}]', APPLICATION_JSON))
        server.expect(requestTo(PROJECT + '/test-cycles?parentId=5&parentType=release&expand=descendants'))
                .andRespond(slow(150, '[{"id":30,"name":"SIT","pid":"CL-30"}]'))
        server.expect(requestTo(PROJECT + '/test-cycles?parentId=6&parentType=release&expand=descendants'))
                .andRespond(slow(10, '[{"id":40,"name":"UAT","pid":"CL-40"}]'))

        expect:
        qtest.cycles(1)*.id() == [30L, 40L]
        mostInFlight.get() == 2
    }

    def "a refusal to list the test runs is reported with its reason"() {
        given:
        server.expect(requestTo(PROJECT + '/test-cycles/10')).andRespond(withSuccess('{"id":10}', APPLICATION_JSON))
        server.expect(requestTo(RUNS + '1&pageSize=999')).andRespond(withServerError())

        when:
        qtest.runs(1, 10)

        then:
        def refused = thrown(UncheckedIOException)
        refused.message.startsWith('qTest could not list the test runs of test cycle 10: 500')
    }

    private static String log(long runId) {
        PROJECT + "/test-runs/$runId/test-logs/last-run"
    }

    private static Map runItem(long id, String status, String name = "Run $id") {
        [id        : id, name: name, pid: "TR-$id".toString(), test_case_version_id: 7,
         properties: [[field_id: 1, field_name: 'Assigned To', field_value: '[3]', field_value_name: 'qtest-bot']] +
                 (status ? [[field_id: 2, field_name: 'Status', field_value: '601', field_value_name: status]] : [])]
    }

    private static String runPage(List items, int total) {
        JsonOutput.toJson([links: [], page: 1, page_size: QTestAdapter.PAGE_SIZE, total: total, items: items])
    }

    /**
     * Answers after a while, counting the answers under way at once.
     */
    private ResponseCreator slow(long millis, String json) {
        { ClientHttpRequest request ->
            int now = inFlight.incrementAndGet()
            mostInFlight.accumulateAndGet(now, { a, b -> Math.max(a, b) })
            try {
                Thread.sleep(millis)
            } catch (InterruptedException e) {
                throw new InterruptedIOException('interrupted')
            } finally {
                inFlight.decrementAndGet()
            }
            withSuccess(json, APPLICATION_JSON).createResponse(request)
        } as ResponseCreator
    }
}
