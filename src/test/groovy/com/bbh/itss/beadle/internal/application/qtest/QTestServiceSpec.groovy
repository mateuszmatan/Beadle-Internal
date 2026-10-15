package com.bbh.itss.beadle.internal.application.qtest

import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.application.qtest.QTestService.CYCLE_REQUIRED
import static com.bbh.itss.beadle.internal.support.Fixtures.cycle
import static com.bbh.itss.beadle.internal.support.Fixtures.project
import static com.bbh.itss.beadle.internal.support.Fixtures.run

class QTestServiceSpec extends Specification {

    QTestPort qtest = Mock()
    def service = new QTestService(qtest)

    def "projects, cycles, runs and the summary come from qTest as they are"() {
        given:
        def summary = new QTestSummary(1, 1, 0, 0, 0)

        when:
        def projects = service.projects()
        def cycles = service.cycles(1)
        def runs = service.runs(1, 10)
        def counts = service.summary(1, 10)

        then:
        1 * qtest.projects() >> [project()]
        1 * qtest.cycles(1) >> [cycle()]
        1 * qtest.runs(1, 10) >> [run()]
        1 * qtest.summary(1, 10) >> summary
        projects == [project()]
        cycles == [cycle()]
        runs == [run()]
        counts == summary
    }

    def "the runs of no cycle are refused before qTest is asked"() {
        when:
        service.runs(1, null)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['cycleId']
        refused.problems*.message == [CYCLE_REQUIRED]
        0 * qtest._
    }
}
