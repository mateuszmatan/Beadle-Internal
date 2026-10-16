package com.bbh.itss.beadle.internal.application.status

import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.domain.status.SystemStatus
import spock.lang.Specification

class StatusServiceSpec extends Specification {

    JiraPort jira = Stub()
    CyberTrackPort cyberTrack = Stub()
    ConfluencePort confluence = Stub()
    QTestPort qtest = Stub()
    def service = new StatusService(jira, cyberTrack, confluence, qtest)

    def "the status says of every system whether it is connected and in which mode it runs"() {
        given:
        jira.connected() >> true
        cyberTrack.connected() >> false
        confluence.connected() >> true
        qtest.connected() >> false

        when:
        def status = service.status()

        then:
        status.jira() == new SystemStatus(true, 'live')
        status.cyberTrack() == new SystemStatus(false, 'demo')
        status.confluence() == new SystemStatus(true, 'live')
        status.qtest() == new SystemStatus(false, 'demo')
    }
}
