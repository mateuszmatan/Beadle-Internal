package com.bbh.itss.beadle.internal.adapter.out.qtest

import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import spock.lang.Specification

import java.time.Instant

import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class DemoQTestAdapterSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T10:00:00Z')

    def qtest = new DemoQTestAdapter(fixed(NOW, UTC))

    def "the demo has steady projects with cycles, each with runs whose ids and pids match"() {
        when:
        def projects = qtest.projects()
        def cycles = qtest.cycles(1)
        def runs = qtest.runs(1, 10)

        then:
        !qtest.connected()
        projects*.name() == ['CertScanner', 'PayHub']
        projects*.id() == [1L, 2L]
        cycles*.pid() == ['CL-10', 'CL-11', 'CL-12']
        cycles*.name() == ['Release 1.3', 'Release 1.4', 'Nightly regression']
        runs.size() == 28
        runs*.id().unique().size() == runs.size()
        runs.every { it.pid() == 'TR-' + it.id() && it.name() }
        runs.every { it.status() in ['Passed', 'Failed', 'Blocked', 'Unexecuted'] }
        runs.every { (it.status() == 'Unexecuted') == (it.executedAt() == null) }
        runs.findAll { it.executedAt() }.every { it.executedAt().isBefore(NOW) }
        qtest.runs(1, 10) == runs
        qtest.cycles(2)*.pid() == ['CL-20', 'CL-21']
    }

    def "a finished cycle is executed in full, an open one has a quarter of its runs unexecuted"() {
        when:
        def finished = qtest.summary(1, 10)
        def open = qtest.summary(1, 11)

        then:
        finished == QTestSummary.of(qtest.runs(1, 10))
        finished.total() == 28
        finished.unexecuted() == 0
        finished.passed() + finished.failed() + finished.blocked() == 28
        open.total() == 34
        open.unexecuted() == 9
        open.passed() + open.failed() + open.blocked() == 25
    }

    def "an unknown project or cycle is not found"() {
        when:
        qtest.cycles(9)

        then:
        def project = thrown(NoSuchElementException)
        project.message == 'qTest project 9 does not exist'

        when:
        qtest.runs(1, 20)

        then:
        def cycle = thrown(NoSuchElementException)
        cycle.message == 'qTest test cycle 20 does not exist'

        when:
        qtest.summary(2, 10)

        then:
        thrown(NoSuchElementException)
    }

    def "the demo is used only while the qTest URL is blank"() {
        given:
        def runner = new ApplicationContextRunner().withUserConfiguration(Adapters)
                .withBean(java.time.Clock, { fixed(NOW, UTC) } as java.util.function.Supplier<java.time.Clock>)

        expect:
        runner.withPropertyValues(properties as String[]).run { context ->
            assert context.getBeansOfType(QTestPort).values()*.class == types
        }

        where:
        properties                                  || types
        []                                          || [DemoQTestAdapter]
        ['beadle.qtest.url=']                       || [DemoQTestAdapter]
        ['beadle.qtest.url=https://qtest.bbh.com']  || []
    }

    @EnableConfigurationProperties(QTestProperties)
    @Import(DemoQTestAdapter)
    static class Adapters {
    }
}
