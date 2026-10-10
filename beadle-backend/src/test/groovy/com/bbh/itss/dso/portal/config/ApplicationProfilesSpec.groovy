package com.bbh.itss.dso.portal.config

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static org.springframework.boot.WebApplicationType.NONE

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/BEADLE',
                               DB_USERNAME: 'BEADLE', DB_PASSWORD: 'secret']

    def "without a profile Beadle runs locally on port 8081 on H2 in the home folder with demo data"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.defaultProfiles as List == ['local']
        env.getProperty('server.port') == '8081'
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/bbh-devsecops/beadle/beadle;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        environment([], [BEADLE_DATA_DIR: '/var/beadle']).getProperty('spring.datasource.url')
                .startsWith('jdbc:h2:file:/var/beadle/beadle;')
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'none'
        env.getProperty('dso.demo-data') == 'true'
        env.getProperty('management.endpoint.health.show-details') == 'always'
    }

    def "the #profile profile runs on the Oracle database given by the environment"() {
        when:
        def env = environment([profile], ORACLE + variables)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        env.getProperty('spring.datasource.url') == ORACLE.DB_URL
        env.getProperty('spring.datasource.username') == 'BEADLE'
        env.getProperty('spring.datasource.password') == 'secret'
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('dso.demo-data') == 'false'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == healthDetails
        env.getProperty('logging.level.com.bbh.itss.dso') == logLevel

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
