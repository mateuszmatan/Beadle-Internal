package com.bbh.itss.dso.portal.config

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static org.springframework.boot.WebApplicationType.NONE

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
        def env = environment([profile], ORACLE + variables)

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

    def "InfluxDB and Grafana are links to their own servers, given by the environment"() {
        when:
        def env = environment([], variables)

        then:
        ['url', 'token', 'org', 'bucket'].collect { env.getProperty("dso.influx.$it") } == influx
        (0..1).collectMany { instance ->
            ['name', 'dashboard-url', 'security-dashboard-url'].collect { env.getProperty("dso.grafana.instances[$instance].$it") }
        } == grafana

        where:
        variables << [[:], [INFLUX_URL: 'https://influx.bbh.com', INFLUX_TOKEN: 'token', INFLUX_ORG: 'BBH',
                            INFLUX_BUCKET: 'metrics', GRAFANA_DASHBOARD_URL: 'https://grafana.bbh.com/d/adzfc54123/p',
                            GRAFANA_SECURITY_DASHBOARD_URL: 'https://grafana.bbh.com/d/ad2trcm/s', GRAFANA_2_NAME: 'Grafana prod',
                            GRAFANA_2_DASHBOARD_URL: 'https://grafana-prod.bbh.com/d/adzfc54123/p']]
        influx << [['', '', 'DevSecOps', 'DORA-metrics'], ['https://influx.bbh.com', 'token', 'BBH', 'metrics']]
        grafana << [['Grafana', '', '', 'Grafana 2', '', ''],
                    ['Grafana', 'https://grafana.bbh.com/d/adzfc54123/p', 'https://grafana.bbh.com/d/ad2trcm/s',
                     'Grafana prod', 'https://grafana-prod.bbh.com/d/adzfc54123/p', '']]
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
