package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.JIRA
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.SUCCESS
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class CallLogConfigurationSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')

    List<CallLogEntry> recorded = []
    boolean callLogDown = false
    int connectedCalls = 0

    def runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration))
            .withUserConfiguration(CallLogConfiguration)
            .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)
            .withBean(CallLogPort, {
                [record: { CallLogEntry entry ->
                    if (callLogDown) {
                        throw new IllegalStateException('database down')
                    }
                    recorded << entry
                    entry
                 }, find: { CallLogQuery query -> new CallLogPage(0, 0, 0, []) }] as CallLogPort
            } as Supplier<CallLogPort>)
            .withBean(JiraPort, {
                [connected: { connectedCalls++; false },
                 versions : { String project -> Thread.sleep(2); [version()] },
                 epics    : { String project, String fixVersion -> throw new UncheckedIOException('Jira could not read the epics of ' + fixVersion + ': 503', new IOException()) },
                 stories  : { String project, String fixVersion, Collection<String> keys -> [] }] as JiraPort
            } as Supplier<JiraPort>)
            .withBean(ConfluencePort, {
                [connected: { true }, find: { String s, String t -> [] }, get: { String id -> page() },
                 create   : { newOne -> page() }, update: { String id, update -> page() }] as ConfluencePort
            } as Supplier<ConfluencePort>)

    def cleanup() {
        CallerContext.clear()
    }

    def "every call of a port is written to the call log with the caller, its target, outcome and duration"() {
        given:
        CallerContext.set(new Caller('Mateusz Matan', 'corr-1'))

        expect:
        runner.run { ApplicationContext context ->
            def jira = context.getBean(JiraPort)
            def confluence = context.getBean(ConfluencePort)
            assert jira.versions('CERT') == [version()]
            assert jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) == []
            assert !jira.connected()
            assert confluence.create(newPage()) == page()
            assert confluence.update('100001', pageUpdate()) == page()
            assert connectedCalls == 1
            assert recorded.size() == 4
            with(recorded[0]) {
                assert id() == null
                assert at() == NOW
                assert userName() == 'Mateusz Matan'
                assert correlationId() == 'corr-1'
                assert system() == JIRA
                assert operation() == 'versions'
                assert target() == 'CERT'
                assert outcome() == SUCCESS
                assert message() == null
                assert durationMs() >= 2
            }
            assert recorded[1].target() == 'CERT CERT 1.4 CERT-12,CERT-13'
            assert recorded[2].system() == CONFLUENCE && recorded[2].operation() == 'create' && recorded[2].target() == 'DSO/Change CHG0031001'
            assert recorded[3].operation() == 'update' && recorded[3].target() == '100001 Beadle release notes'
            true
        }
    }

    def "a failing call is written as a failure with its message and still thrown, without a caller when none is known"() {
        expect:
        runner.run { ApplicationContext context ->
            try {
                context.getBean(JiraPort).epics('CERT', 'CERT 1.4')
                assert false
            } catch (UncheckedIOException e) {
                assert e.message == 'Jira could not read the epics of CERT 1.4: 503'
            }
            with(recorded[0]) {
                assert userName() == null
                assert correlationId() == null
                assert system() == JIRA
                assert operation() == 'epics'
                assert target() == 'CERT CERT 1.4'
                assert outcome() == FAILURE
                assert message() == 'Jira could not read the epics of CERT 1.4: 503'
            }
            true
        }
    }

    def "a call log that cannot be written never fails the call"() {
        given:
        callLogDown = true

        expect:
        runner.run { ApplicationContext context ->
            assert context.getBean(JiraPort).versions('CERT') == [version()]
            assert recorded == []
            true
        }
    }

    def "the call log port itself is not intercepted"() {
        expect:
        runner.run { ApplicationContext context ->
            assert context.getBean(CallLogPort).find(CallLogQuery.of(null, null, null, 0, 10)).total() == 0
            assert recorded == []
            true
        }
    }
}
