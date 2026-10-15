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
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.change-log') == 'classpath:db/changelog/db.changelog-master.yaml'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('management.endpoint.health.show-details') == 'always'
        SYSTEMS.every { env.getProperty("beadle.${it}.url") == '' && env.getProperty("beadle.${it}.token") == '' }
        env.getProperty('beadle.services.token') == ''
        env.getProperty('beadle.gui.url') == 'http://localhost:8081'
        env.getProperty('beadle.cybertrack.project-key') == 'SCP'
        env.getProperty('beadle.cybertrack.issue-type') == 'Task'
        env.getProperty('beadle.cybertrack.poll') == 'PT5M'
    }

    def "the service token, Beadle-GUI and the external systems are given by the environment"() {
        when:
        def env = environment([], [BEADLE_SERVICE_TOKEN: 'shared-secret', BEADLE_GUI_URL: 'http://beadle:8081',
                                   JIRA_URL: 'https://jira.bbh.com', JIRA_TOKEN: 'jira-token',
                                   CYBERTRACK_URL: 'https://cybertrack.bbh.com', CYBERTRACK_TOKEN: 'cybertrack-token',
                                   CYBERTRACK_PROJECT_KEY: 'SEC', CYBERTRACK_ISSUE_TYPE: 'Security Task',
                                   CYBERTRACK_POLL: 'PT1M', CONFLUENCE_URL: 'https://confluence.bbh.com',
                                   CONFLUENCE_TOKEN: 'confluence-token', QTEST_URL: 'https://qtest.bbh.com',
                                   QTEST_TOKEN: 'qtest-token'])

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

    private static Environment environment(List<String> profiles, Map<String, String> variables) {
        def application = new SpringApplicationBuilder(Empty)
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
