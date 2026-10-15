package com.bbh.itss.beadle.internal.config

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static org.springframework.boot.WebApplicationType.NONE

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/BEADLE',
                               DB_USERNAME: 'BEADLE_INTERNAL', DB_PASSWORD: 'secret']
    static final List<String> SYSTEMS = ['jira', 'cybertrack', 'confluence', 'qtest']
    static final Map REAL_SYSTEMS = [JIRA_URL      : 'https://jira.bbh.com', CYBERTRACK_URL: 'https://cybertrack.bbh.com',
                                     CONFLUENCE_URL: 'https://confluence.bbh.com', QTEST_URL: 'https://qtest.bbh.com']

    def "without a profile Beadle-Internal runs locally on port 8082 on H2 in the home folder with the demo adapters"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.defaultProfiles as List == ['local']
        env.getProperty('server.port') == '8082'
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/bbh-devsecops/beadle-internal/beadle-internal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        environment([], [BEADLE_INTERNAL_DATA_DIR: '/var/beadle-internal']).getProperty('spring.datasource.url')
                .startsWith('jdbc:h2:file:/var/beadle-internal/beadle-internal;')
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'none'
        env.getProperty('spring.liquibase.change-log') == 'classpath:db/changelog/db.changelog-master.yaml'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('management.endpoint.health.show-details') == 'always'
        SYSTEMS.every { env.getProperty("beadle.${it}.url") == '' && env.getProperty("beadle.${it}.token") == '' }
        SYSTEMS.every { env.getProperty("beadle.${it}.demo") == 'false' }
        env.getProperty('beadle.services.token') == ''
        env.getProperty('beadle.gui.url') == 'http://localhost:8081'
        env.getProperty('beadle.cybertrack.project-key') == 'SCP'
        env.getProperty('beadle.cybertrack.issue-type') == 'Task'
        env.getProperty('beadle.cybertrack.poll') == 'PT5M'
        env.getProperty('beadle.cybertrack.closed-statuses') == "Done,Closed,Resolved,Cancelled,Canceled,Rejected,Won't Do"
        env.getProperty('beadle.cybertrack.max-misses') == '12'
        env.getProperty('beadle.jira.epic-link-field') == 'customfield_10014'
        env.getProperty('beadle.qtest.concurrency') == '8'
        env.getProperty('beadle.qtest.budget') == '20s'
        env.getProperty('spring.http.clients.connect-timeout') == '5s'
        env.getProperty('spring.http.clients.read-timeout') == '30s'
    }

    def "the service token, Beadle-GUI and the external systems are given by the environment"() {
        when:
        def env = environment([], [BEADLE_SERVICE_TOKEN: 'shared-secret', BEADLE_GUI_URL: 'http://beadle:8081',
                                   JIRA_URL: 'https://jira.bbh.com', JIRA_TOKEN: 'jira-token', JIRA_EPIC_LINK_FIELD: 'customfield_10008',
                                   CYBERTRACK_URL: 'https://cybertrack.bbh.com', CYBERTRACK_TOKEN: 'cybertrack-token',
                                   CYBERTRACK_PROJECT_KEY: 'SEC', CYBERTRACK_ISSUE_TYPE: 'Security Task',
                                   CYBERTRACK_POLL: 'PT1M', CYBERTRACK_CLOSED_STATUSES: 'Remediated,Risk Accepted',
                                   CYBERTRACK_MAX_MISSES: '3', CONFLUENCE_URL: 'https://confluence.bbh.com',
                                   CONFLUENCE_TOKEN: 'confluence-token', QTEST_URL: 'https://qtest.bbh.com',
                                   QTEST_TOKEN: 'qtest-token', QTEST_CONCURRENCY: '4', QTEST_BUDGET: '15s'])

        then:
        env.getProperty('beadle.services.token') == 'shared-secret'
        env.getProperty('beadle.gui.url') == 'http://beadle:8081'
        SYSTEMS.collect { env.getProperty("beadle.${it}.url") } ==
                ['https://jira.bbh.com', 'https://cybertrack.bbh.com', 'https://confluence.bbh.com', 'https://qtest.bbh.com']
        SYSTEMS.collect { env.getProperty("beadle.${it}.token") } ==
                ['jira-token', 'cybertrack-token', 'confluence-token', 'qtest-token']
        env.getProperty('beadle.cybertrack.project-key') == 'SEC'
        env.getProperty('beadle.cybertrack.issue-type') == 'Security Task'
        env.getProperty('beadle.cybertrack.poll') == 'PT1M'
        env.getProperty('beadle.cybertrack.closed-statuses') == 'Remediated,Risk Accepted'
        env.getProperty('beadle.cybertrack.max-misses') == '3'
        env.getProperty('beadle.jira.epic-link-field') == 'customfield_10008'
        env.getProperty('beadle.qtest.concurrency') == '4'
        env.getProperty('beadle.qtest.budget') == '15s'
    }

    def "the #profile profile runs on the Oracle database given by the environment"() {
        when:
        def env = environment([profile], ORACLE + variables)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        env.getProperty('spring.datasource.url') == ORACLE.DB_URL
        env.getProperty('spring.datasource.username') == 'BEADLE_INTERNAL'
        env.getProperty('spring.datasource.password') == 'secret'
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == healthDetails
        env.getProperty('logging.level.com.bbh.itss.beadle') == logLevel

        where:
        profile | variables            || poolSize | healthDetails | logLevel
        'rd'    | [:]                  || '5'      | 'always'      | 'DEBUG'
        'qc'    | [:]                  || '10'     | 'always'      | 'INFO'
        'prod'  | [:]                  || '20'     | 'never'       | 'INFO'
        'prod'  | [DB_POOL_SIZE: '40'] || '40'     | 'never'       | 'INFO'
    }

    def "the #profile profile does not start without the database address"() {
        when:
        environment([profile], [:]).getProperty('spring.datasource.url')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('DB_URL')

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "the #profile profile starts on the real systems"() {
        when:
        def env = environment([profile], ORACLE + REAL_SYSTEMS, RealSystemsConfiguration)

        then:
        SYSTEMS.collect { env.getProperty("beadle.${it}.url") } == REAL_SYSTEMS.values() as List

        where:
        profile << ['rd', 'qc', 'prod']
    }

    def "the #profile profile does not start while #blank is blank, since a demo adapter would answer in its place"() {
        when:
        environment([profile], ORACLE + REAL_SYSTEMS + variables, RealSystemsConfiguration)

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("The rd, qc and prod profiles need the real systems, but $blank ${verb} blank")
        e.message.contains('<SYSTEM>_DEMO=true')

        where:
        profile | variables                                                              || blank                                                  | verb
        'prod'  | [CYBERTRACK_URL: '']                                                   || 'CYBERTRACK_URL'                                       | 'is'
        'qc'    | [JIRA_URL: ' ', QTEST_URL: '']                                         || 'JIRA_URL, QTEST_URL'                                  | 'are'
        'rd'    | [JIRA_URL: '', CYBERTRACK_URL: '', CONFLUENCE_URL: '', QTEST_URL: ''] || 'JIRA_URL, CYBERTRACK_URL, CONFLUENCE_URL, QTEST_URL' | 'are'
        'prod'  | [CYBERTRACK_URL: '', CYBERTRACK_DEMO: 'false']                         || 'CYBERTRACK_URL'                                       | 'is'
    }

    def "a system runs on its demo on #profile only when asked for on purpose"() {
        when:
        def env = environment([profile], ORACLE + REAL_SYSTEMS + [CONFLUENCE_URL: '', CONFLUENCE_DEMO: 'true'],
                RealSystemsConfiguration)

        then:
        env.getProperty('beadle.confluence.url') == ''
        env.getProperty('beadle.confluence.demo') == 'true'

        where:
        profile << ['rd', 'prod']
    }

    def "the local profile runs on the demo adapters without asking"() {
        when:
        def env = environment([], [:], RealSystemsConfiguration)

        then:
        env.defaultProfiles as List == ['local']
        SYSTEMS.every { env.getProperty("beadle.${it}.url") == '' }
    }

    private static Environment environment(List<String> profiles, Map<String, String> variables,
                                           Class<?>... configurations) {
        def application = new SpringApplicationBuilder(([Empty] + (configurations as List)) as Class<?>[])
                .web(NONE)
                .logStartupInfo(false)
                .properties(variables.collectEntries { name, value -> [name, value] } as Map<String, Object>)
        if (profiles) {
            application.profiles(profiles as String[])
        }
        def context = application.run()
        def environment = context.environment
        context.close()
        environment
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
