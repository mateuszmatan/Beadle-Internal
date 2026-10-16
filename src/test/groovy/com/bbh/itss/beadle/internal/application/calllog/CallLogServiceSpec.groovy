package com.bbh.itss.beadle.internal.application.calllog

import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.support.Fixtures.callLogEntry

class CallLogServiceSpec extends Specification {

    CallLogPort callLog = Mock()
    def service = new CallLogService(callLog)

    def "the call log is searched as asked"() {
        given:
        def query = CallLogQuery.of('corr-1', null, null, 0, 20)
        def page = new CallLogPage(0, 20, 1, [callLogEntry()])

        when:
        def found = service.find(query)

        then:
        1 * callLog.find(query) >> page
        found == page
    }
}
