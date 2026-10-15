package com.bbh.itss.beadle.internal.domain.status

import spock.lang.Specification

class SystemStatusSpec extends Specification {

    def "a connected system runs live, a disconnected one in demo mode"() {
        expect:
        SystemStatus.of(true) == new SystemStatus(true, 'live')
        SystemStatus.of(false) == new SystemStatus(false, 'demo')
    }
}
