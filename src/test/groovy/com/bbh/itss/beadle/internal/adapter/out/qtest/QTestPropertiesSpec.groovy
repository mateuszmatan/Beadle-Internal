package com.bbh.itss.beadle.internal.adapter.out.qtest

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import spock.lang.Specification

import java.time.Duration

class QTestPropertiesSpec extends Specification {

    def runner = new ApplicationContextRunner().withUserConfiguration(Properties)

    def "qTest is read #reads"() {
        expect:
        runner.withPropertyValues((['beadle.qtest.url=https://qtest.bbh.com', 'beadle.qtest.token=t'] + values) as String[]).run { context ->
            assert context.getBean(QTestProperties) == new QTestProperties('https://qtest.bbh.com', 't', concurrency, budget)
        }

        where:
        reads                                        | values                                                        || concurrency | budget
        '8 reads at a time within 20 seconds'        | []                                                            || 8           | Duration.ofSeconds(20)
        'as the environment says'                    | ['beadle.qtest.concurrency=4', 'beadle.qtest.budget=PT25S']   || 4           | Duration.ofSeconds(25)
        'with a budget in the short notation'        | ['beadle.qtest.budget=1500ms']                                 || 8           | Duration.ofMillis(1500)
    }

    def "a #setting of #value does not start"() {
        expect:
        runner.withPropertyValues("beadle.qtest.$setting=$value").run { context ->
            Throwable failure = context.startupFailure
            while (failure?.cause) {
                failure = failure.cause
            }
            assert failure instanceof IllegalArgumentException
            assert failure.message == message
        }

        where:
        setting       | value  || message
        'concurrency' | '0'    || 'beadle.qtest.concurrency must be between 1 and 50, not 0'
        'concurrency' | '51'   || 'beadle.qtest.concurrency must be between 1 and 50, not 51'
        'budget'      | '0s'   || 'beadle.qtest.budget must be more than 0 and less than the 30 seconds Beadle-GUI waits for an answer, not PT0S'
        'budget'      | '30s'  || 'beadle.qtest.budget must be more than 0 and less than the 30 seconds Beadle-GUI waits for an answer, not PT30S'
        'budget'      | '-1s'  || 'beadle.qtest.budget must be more than 0 and less than the 30 seconds Beadle-GUI waits for an answer, not PT-1S'
    }

    def "the adapter built in code reads with the usual concurrency and budget"() {
        expect:
        new QTestProperties('https://qtest.bbh.com', 't') == new QTestProperties('https://qtest.bbh.com', 't', 8, Duration.ofSeconds(20))
    }

    @EnableConfigurationProperties(QTestProperties)
    static class Properties {
    }
}
