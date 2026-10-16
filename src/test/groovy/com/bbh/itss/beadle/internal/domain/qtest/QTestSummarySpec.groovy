package com.bbh.itss.beadle.internal.domain.qtest

import spock.lang.Specification

import static com.bbh.itss.beadle.internal.support.Fixtures.run

class QTestSummarySpec extends Specification {

    def "the summary counts the runs by status, whatever the case, and the rest as unexecuted"() {
        given:
        def runs = [run(status: 'Passed'), run(status: 'PASSED'), run(status: 'Failed'), run(status: 'blocked'),
                    run(status: 'Unexecuted', executedAt: null), run(status: 'Incomplete'), run(status: null)]

        expect:
        QTestSummary.of(runs) == new QTestSummary(7, 2, 1, 1, 3)
        QTestSummary.of([]) == new QTestSummary(0, 0, 0, 0, 0)
    }
}
