package com.bbh.itss.dso.portal.config

import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/DSOPORTAL',
                               DB_USERNAME: 'DSO_PORTAL', DB_PASSWORD: 'secret']

    def "without a profile the portal runs locally on H2 with demo data"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.defaultProfiles as List == ['local']
        env.getProperty('spring.datasource.url').startsWith('jdbc:h2:file:./data/dso-portal;MODE=Oracle')
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'none'
        env.getProperty('dso.demo-data') == 'true'
        env.getProperty('management.endpoint.health.show-details') == 'always'
    }

    def "the #profile profile runs on the Oracle database given by the environment"() {
        when:
        def env = environment([profile], ORACLE)

        then:
        env.activeProfiles as List == [profile, 'oracle']
        env.getProperty('spring.datasource.url') == ORACLE.DB_URL
        env.getProperty('spring.datasource.username') == 'DSO_PORTAL'
        env.getProperty('spring.datasource.password') == 'secret'
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'validate'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('dso.demo-data') == 'false'
        env.getProperty('spring.datasource.hikari.maximum-pool-size') == poolSize
        env.getProperty('management.endpoint.health.show-details') == healthDetails
        env.getProperty('logging.level.com.bbh.itss.dso') == logLevel

        where:
        profile || poolSize | healthDetails | logLevel
        'rd'    || '5'      | 'always'      | 'DEBUG'
        'qc'    || '10'     | 'always'      | 'INFO'
        'prod'  || '20'     | 'never'       | 'INFO'
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

    def "the pool size can be set for any Oracle environment"() {
        expect:
        environment(['prod'], ORACLE + [DB_POOL_SIZE: '40']).getProperty('spring.datasource.hikari.maximum-pool-size') == '40'
    }

    private static Environment environment(List<String> profiles, Map<String, String> variables) {
        def application = new SpringApplicationBuilder(Empty)
                .web(WebApplicationType.NONE)
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
