package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

class CyberTrackFollowUpConfigurationSpec extends Specification {

    def runner = new ApplicationContextRunner().withUserConfiguration(Properties, CyberTrackFollowUpConfiguration)

    def "the tickets are followed until #ending"() {
        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBean(FollowUp) == expected
        }

        where:
        ending                                         | properties                                                                                   || expected
        'a usual final status or twelve misses'        | []                                                                                           || FollowUp.DEFAULT
        'a final status of their own workflow'         | ['beadle.cybertrack.closed-statuses=Remediated, Risk Accepted', 'beadle.cybertrack.max-misses=3'] || new FollowUp(['Remediated', 'Risk Accepted'], 3)
    }

    def "a follow-up that would never end does not start"() {
        expect:
        runner.withPropertyValues('beadle.cybertrack.max-misses=0').run { context ->
            Throwable failure = context.startupFailure
            while (failure?.cause) {
                failure = failure.cause
            }
            assert failure instanceof IllegalArgumentException
            assert failure.message == 'beadle.cybertrack.max-misses must be 1 or more, not 0'
        }
    }

    @EnableConfigurationProperties(CyberTrackFollowUpProperties)
    static class Properties {
    }
}
