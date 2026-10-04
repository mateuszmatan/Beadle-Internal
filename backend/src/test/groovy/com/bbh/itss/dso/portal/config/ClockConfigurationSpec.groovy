package com.bbh.itss.dso.portal.config

import spock.lang.Specification

import java.time.ZoneOffset

class ClockConfigurationSpec extends Specification {

    def "the application clock runs in UTC"() {
        expect:
        new ClockConfiguration().clock().zone == ZoneOffset.UTC
    }
}
