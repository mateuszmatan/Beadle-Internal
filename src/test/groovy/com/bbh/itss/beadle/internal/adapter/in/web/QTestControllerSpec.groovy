package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.qtest.port.in.QTestUseCase
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import static com.bbh.itss.beadle.internal.support.Fixtures.cycle
import static com.bbh.itss.beadle.internal.support.Fixtures.project
import static com.bbh.itss.beadle.internal.support.Fixtures.run
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class QTestControllerSpec extends ControllerSpecification {

    QTestUseCase qtest = Mock()

    @Override
    Object controller() {
        new QTestController(qtest)
    }

    def "the projects are listed with their ids and names"() {
        when:
        def response = call(get('/api/internal/qtest/projects'))

        then:
        1 * qtest.projects() >> [project(), project(id: 2L, name: 'PayHub')]
        response.status == 200
        bodyOf(response) == [[id: 1, name: 'CertScanner'], [id: 2, name: 'PayHub']]
    }

    def "the test cycles of a project are listed with their pids"() {
        when:
        def response = call(get('/api/internal/qtest/projects/1/test-cycles'))

        then:
        1 * qtest.cycles(1) >> [cycle()]
        response.status == 200
        bodyOf(response) == [[id: 10, name: 'Release 1.4', pid: 'CL-10']]
    }

    def "the test runs of a cycle are listed with their status and execution time"() {
        when:
        def response = call(get('/api/internal/qtest/projects/1/test-runs').param('cycleId', '10'))

        then:
        1 * qtest.runs(1, 10) >> [run(), run(id: 101L, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null)]
        response.status == 200
        bodyOf(response) == [[id: 100, name: 'Sign in with SSO', pid: 'TR-100', status: 'Passed', executedAt: '2026-10-15T09:00:00Z'],
                             [id: 101, name: 'Sign out', pid: 'TR-101', status: 'Unexecuted', executedAt: null]]
    }

    def "the summary of a cycle counts its runs"() {
        when:
        def response = call(get('/api/internal/qtest/projects/1/test-cycles/10/summary'))

        then:
        1 * qtest.summary(1, 10) >> new QTestSummary(42, 40, 1, 0, 1)
        response.status == 200
        bodyOf(response) == [total: 42, passed: 40, failed: 1, blocked: 0, unexecuted: 1]
    }

    def "the test runs without a cycle are refused with the problem"() {
        when:
        def response = call(get('/api/internal/qtest/projects/1/test-runs'))

        then:
        1 * qtest.runs(1, null) >> { throw InvalidRequestException.of('cycleId', 'is required') }
        response.status == 400
        bodyOf(response).problems == [[field: 'cycleId', message: 'is required']]
    }

    def "a project id that is no number is malformed, an unknown project 404 and a refusing qTest 502"() {
        when:
        def malformed = call(get('/api/internal/qtest/projects/x/test-cycles'))
        def missing = call(get('/api/internal/qtest/projects/9/test-cycles'))
        def refused = call(get('/api/internal/qtest/projects'))

        then:
        1 * qtest.cycles(9) >> { throw new NoSuchElementException('qTest project 9 does not exist') }
        1 * qtest.projects() >> { throw new UncheckedIOException('qTest could not list the projects: 401 Unauthorized', new IOException()) }
        malformed.status == 400
        bodyOf(malformed).detail == "The value given for 'id' is not one this endpoint can read."
        missing.status == 404
        bodyOf(missing).detail == 'qTest project 9 does not exist'
        refused.status == 502
        bodyOf(refused).detail == 'qTest could not list the projects: 401 Unauthorized'
    }

    def "without the service token #path is 401 (#authorization)"() {
        when:
        def response = callWithout(get('/api/internal/qtest/projects' + path), authorization)

        then:
        response.status == 401
        bodyOf(response).title == 'Unauthorized'
        0 * qtest._

        where:
        [path, authorization] << [['', '/1/test-cycles', '/1/test-runs?cycleId=10', '/1/test-cycles/10/summary'],
                                  unauthorized()].combinations()
    }
}
