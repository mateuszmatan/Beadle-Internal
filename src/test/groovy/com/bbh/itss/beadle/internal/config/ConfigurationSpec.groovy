package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.adapter.out.qtest.QTestProperties
import com.bbh.itss.beadle.internal.application.callback.port.out.CallbackPort
import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.application.calllog.port.out.CallLogPort
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort
import com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import com.bbh.itss.beadle.internal.domain.cybertrack.FollowUp
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.client.RestClient
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.util.function.Supplier

import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.CONFLUENCE
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.GUI
import static com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem.JIRA
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.FAILURE
import static com.bbh.itss.beadle.internal.domain.calllog.Outcome.SUCCESS
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.statusCallback
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC
import static org.springframework.boot.WebApplicationType.NONE

/**
 * The wiring of Beadle-Internal: the profiles and the variables that configure them, the refusal to run rd, qc or prod
 * on a demo, the adapter each system gets and the call log around every port.
 */
class ConfigurationSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final Caller USER = new Caller('Mateusz Matan', 'corr-1')
    static final Map ORACLE = [DB_URL: 'jdbc:oracle:thin:@//oracle.bbh.test:1521/BEADLE', DB_USERNAME: 'BEADLE_INTERNAL',
                               DB_PASSWORD: 'secret']
    static final Map REAL_SYSTEMS = [JIRA_URL: 'https://jira.bbh.com', CYBERTRACK_URL: 'https://cybertrack.bbh.com',
                                     CONFLUENCE_URL: 'https://confluence.bbh.com', QTEST_URL: 'https://qtest.bbh.com']
    static final Map VARIABLES = [BEADLE_SERVICE_TOKEN  : 'beadle.services.token', BEADLE_GUI_URL: 'beadle.gui.url',
                                  JIRA_URL              : 'beadle.jira.url', JIRA_TOKEN: 'beadle.jira.token',
                                  JIRA_EPIC_LINK_FIELD  : 'beadle.jira.epic-link-field',
                                  CYBERTRACK_URL        : 'beadle.cybertrack.url', CYBERTRACK_TOKEN: 'beadle.cybertrack.token',
                                  CYBERTRACK_PROJECT_KEY: 'beadle.cybertrack.project-key',
                                  CYBERTRACK_ISSUE_TYPE : 'beadle.cybertrack.issue-type', CYBERTRACK_POLL: 'beadle.cybertrack.poll',
                                  CYBERTRACK_CLOSED_STATUSES: 'beadle.cybertrack.closed-statuses',
                                  CYBERTRACK_MAX_MISSES : 'beadle.cybertrack.max-misses',
                                  CONFLUENCE_URL        : 'beadle.confluence.url', CONFLUENCE_TOKEN: 'beadle.confluence.token',
                                  QTEST_URL             : 'beadle.qtest.url', QTEST_TOKEN: 'beadle.qtest.token',
                                  QTEST_CONCURRENCY     : 'beadle.qtest.concurrency', QTEST_BUDGET: 'beadle.qtest.budget',
                                  DB_URL                : 'spring.datasource.url', PORT: 'server.port']

    def "without a profile Beadle-Internal runs locally on port 8082 on H2 in Oracle mode with the demo adapters"() {
        when:
        def env = environment([], [:])

        then:
        env.defaultProfiles as List == ['local']
        [server: env.getProperty('server.port'), ddl: env.getProperty('spring.jpa.hibernate.ddl-auto'),
         health: env.getProperty('management.endpoint.health.show-details'), gui: env.getProperty('beadle.gui.url'),
         urls: ['jira', 'cybertrack', 'confluence', 'qtest'].collect { env.getProperty("beadle.${it}.url") }.unique(),
         poll: env.getProperty('beadle.cybertrack.poll'), misses: env.getProperty('beadle.cybertrack.max-misses'),
         budget: env.getProperty('beadle.qtest.budget')] ==
                [server: '8082', ddl: 'validate', health: 'always', gui: 'http://localhost:8081', urls: [''], poll: 'PT5M',
                 misses: '12', budget: '20s']
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/bbh-devsecops/beadle-internal/beadle-internal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        environment([], [BEADLE_INTERNAL_DATA_DIR: '/var/beadle']).getProperty('spring.datasource.url')
                .startsWith('jdbc:h2:file:/var/beadle/beadle-internal;')
        new ClockConfiguration().clock().zone == UTC
    }

    def "every setting of an environment comes from its variable"() {
        when:
        def env = environment([], VARIABLES.collectEntries { variable, property -> [variable, "$variable value".toString()] })

        then:
        VARIABLES.findAll { variable, property -> env.getProperty(property) != "$variable value" } == [:]
    }

    def "the #profile profile runs on the Oracle database of the environment, validated as on H2"() {
        when:
        def env = environment([profile], ORACLE + variables)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        [env.getProperty('spring.datasource.url'), env.getProperty('spring.datasource.username'),
         env.getProperty('spring.datasource.password')] == [ORACLE.DB_URL, 'BEADLE_INTERNAL', 'secret']
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == health
        env.getProperty('logging.level.com.bbh.itss.beadle') == level

        where:
        profile | variables            || poolSize | health   | level
        'rd'    | [:]                  || '5'      | 'always' | 'DEBUG'
        'qc'    | [:]                  || '10'     | 'always' | 'INFO'
        'prod'  | [DB_POOL_SIZE: '40'] || '40'     | 'never'  | 'INFO'
    }

    def "the prod profile does not start without the address of its database"() {
        when:
        environment(['prod'], [:]).getProperty('spring.datasource.url')

        then:
        def missing = thrown(IllegalArgumentException)
        missing.message.contains('DB_URL')
    }

    def "#name starts #started, since a demo adapter would answer in place of a blank system"() {
        when:
        def failure = null
        try {
            environment(profile ? [profile] : [], (profile ? ORACLE + REAL_SYSTEMS : [:]) + variables, RealSystemsConfiguration)
        } catch (IllegalStateException e) {
            failure = e.message
        }

        then:
        failure == null ? started == 'on its systems' : failure.startsWith("The rd, qc and prod profiles need the real systems, but $started")

        where:
        profile | variables                                     || started
        'prod'  | [:]                                           || 'on its systems'
        'prod'  | [CYBERTRACK_URL: '']                          || 'CYBERTRACK_URL is blank'
        'qc'    | [JIRA_URL: ' ', QTEST_URL: '']                || 'JIRA_URL, QTEST_URL are blank'
        'rd'    | [CYBERTRACK_URL: '', CYBERTRACK_DEMO: 'false'] || 'CYBERTRACK_URL is blank'
        'rd'    | [CONFLUENCE_URL: '', CONFLUENCE_DEMO: 'true'] || 'on its systems'
        null    | [:]                                           || 'on its systems'
        name = (profile ?: 'local') + (variables ? ' with ' + variables : '')
    }

    def "a system with a URL gets its adapter, and one without it gets its demo"() {
        given:
        def runner = new ApplicationContextRunner()
                .withUserConfiguration(Adapters)
                .withBean(RestClient.Builder, { RestClient.builder() } as Supplier<RestClient.Builder>)
                .withBean(Clock, { Clock.systemUTC() } as Supplier<Clock>)
                .withBean(TrackedTicketPort, { [:] as TrackedTicketPort } as Supplier<TrackedTicketPort>)
                .withPropertyValues('beadle.qtest.concurrency=8', 'beadle.qtest.budget=20s')

        expect:
        runner.withPropertyValues(urls.collect { "beadle.$it=https://${it.tokenize('.')[0]}.bbh.com".toString() } as String[]).run { context ->
            assert [JiraPort, CyberTrackPort, ConfluencePort, QTestPort].collect { context.getBean(it).class.simpleName } == adapters
        }

        where:
        urls                                                          || adapters
        []                                                            || ['DemoJiraAdapter', 'DemoCyberTrackAdapter', 'DemoConfluenceAdapter', 'DemoQTestAdapter']
        ['jira.url', 'cybertrack.url', 'confluence.url', 'qtest.url'] || ['JiraAdapter', 'CyberTrackAdapter', 'ConfluenceAdapter', 'QTestAdapter']
    }

    def "qTest is pressed with #concurrency reads at once and a budget of #budget: #outcome"() {
        expect:
        new ApplicationContextRunner().withUserConfiguration(QTestSettings)
                .withPropertyValues("beadle.qtest.concurrency=$concurrency", "beadle.qtest.budget=$budget").run { context ->
            assert (context.startupFailure == null) == (outcome == 'taken')
        }

        where:
        concurrency | budget || outcome
        8           | '20s'  || 'taken'
        0           | '20s'  || 'refused'
        51          | '20s'  || 'refused'
        8           | '0s'   || 'refused'
        8           | '30s'  || 'refused'
    }

    def "the tickets are followed as the environment says"() {
        expect:
        new ApplicationContextRunner().withUserConfiguration(FollowUpSettings)
                .withPropertyValues('beadle.cybertrack.closed-statuses=Remediated, Risk Accepted', 'beadle.cybertrack.max-misses=3')
                .run { context ->
                    assert new SchedulingConfiguration().followUp(context.getBean(CyberTrackFollowUpProperties)) ==
                            new FollowUp(['Remediated', 'Risk Accepted'], 3)
                }
    }

    def "every call of a port is written to the call log with the caller, target, outcome and duration, a failure too"() {
        given:
        def calls = callLog()

        when:
        calls.runner.run { context ->
            def jira = context.getBean(JiraPort)
            def confluence = context.getBean(ConfluencePort)
            CallerContext.run(USER) {
                assert jira.versions('CERT') == [version()]
                assert jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) == []
                assert !jira.connected()
                assert confluence.create(newPage()) == page()
                assert confluence.update('100001', pageUpdate()) == page()
            }
            CallerContext.run(new Caller('system', 'tick-1')) { context.getBean(CallbackPort).notify(statusCallback()) }
            try {
                jira.epics('CERT', 'CERT 1.4')
                assert false
            } catch (UncheckedIOException e) {
                assert e.message == 'Jira could not read the epics: 503'
            }
            assert context.getBean(CallLogPort).find(CallLogQuery.of(null, null, null, 0, 10)).total() == 0
        }
        def entries = calls.recorded as List<CallLogEntry>

        then:
        entries.collect { [it.userName(), it.correlationId(), it.system(), it.operation(), it.target(), it.outcome(), it.message()] } == [
                ['Mateusz Matan', 'corr-1', JIRA, 'versions', 'CERT', SUCCESS, null],
                ['Mateusz Matan', 'corr-1', JIRA, 'stories', 'CERT CERT 1.4 CERT-12,CERT-13', SUCCESS, null],
                ['Mateusz Matan', 'corr-1', CONFLUENCE, 'create', 'DSO/Change CHG0031001', SUCCESS, null],
                ['Mateusz Matan', 'corr-1', CONFLUENCE, 'update', '100001 Beadle release notes', SUCCESS, null],
                ['system', 'tick-1', GUI, 'notify', 'SCP-1234 In Progress', SUCCESS, null],
                [null, null, JIRA, 'epics', 'CERT CERT 1.4', FAILURE, 'Jira could not read the epics: 503']]
        entries.every { it.id() == null && it.at() == NOW }
        entries[0].durationMs() >= 2
    }

    def "a caller longer than the call log holds is cut to its columns, and a call log that cannot be written fails no call"() {
        given:
        def calls = callLog()

        expect:
        calls.runner.run { context ->
            CallerContext.run(new Caller('u' * 250, 'c' * 120)) { context.getBean(JiraPort).versions('CERT') }
            calls.down = true
            assert context.getBean(JiraPort).versions('CERT') == [version()]
        }
        calls.recorded*.userName() == ['u' * (CallLogEntry.USER_MAX - 3) + '...']
        calls.recorded*.correlationId() == ['c' * (CallLogEntry.CORRELATION_MAX - 3) + '...']
    }

    private Map callLog() {
        def calls = [recorded: [], down: false]
        calls.runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration))
                .withUserConfiguration(CallLogConfiguration)
                .withBean(Clock, { fixed(NOW, UTC) } as Supplier<Clock>)
                .withBean(CallLogPort, {
                    [record: { CallLogEntry entry ->
                        if (calls.down) {
                            throw new IllegalStateException('database down')
                        }
                        calls.recorded << entry
                        entry
                    }, find: { CallLogQuery query -> new CallLogPage(0, 0, 0, []) }] as CallLogPort
                } as Supplier<CallLogPort>)
                .withBean(JiraPort, {
                    [connected: { false },
                     versions : { String project -> Thread.sleep(2); [version()] },
                     epics    : { String project, String fixVersion -> throw new UncheckedIOException('Jira could not read the epics: 503', new IOException()) },
                     stories  : { String project, String fixVersion, Collection<String> keys -> [] }] as JiraPort
                } as Supplier<JiraPort>)
                .withBean(ConfluencePort, { [create: { newOne -> page() }, update: { String id, update -> page() }] as ConfluencePort } as Supplier<ConfluencePort>)
                .withBean(CallbackPort, { [notify: { callback -> }] as CallbackPort } as Supplier<CallbackPort>)
        calls
    }

    private static Environment environment(List<String> profiles, Map<String, String> variables, Class<?>... configurations) {
        def context = new SpringApplicationBuilder(([Empty] + (configurations as List)) as Class<?>[])
                .web(NONE).logStartupInfo(false)
                .properties(variables as Map<String, Object>)
                .profiles(profiles as String[])
                .run()
        context.close()
        context.environment
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(['com.bbh.itss.beadle.internal.adapter.out.jira', 'com.bbh.itss.beadle.internal.adapter.out.cybertrack',
            'com.bbh.itss.beadle.internal.adapter.out.confluence', 'com.bbh.itss.beadle.internal.adapter.out.qtest'])
    @ConfigurationPropertiesScan('com.bbh.itss.beadle.internal.adapter.out')
    static class Adapters {
    }

    @EnableConfigurationProperties(QTestProperties)
    static class QTestSettings {
    }

    @EnableConfigurationProperties(CyberTrackFollowUpProperties)
    static class FollowUpSettings {
    }
}
