package com.bbh.itss.dso.portal.config

import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import spock.lang.Specification

import static org.springframework.boot.WebApplicationType.NONE

class ApplicationProfilesSpec extends Specification {

    static final Map ORACLE = [DB_URL     : 'jdbc:oracle:thin:@//oracle.bbh.test:1521/DSOPORTAL',
                               DB_USERNAME: 'DSO_PORTAL', DB_PASSWORD: 'secret']

    def "the portal runs on port 8080 on a persistent H2 database in the home folder, with no profile and no demo data"() {
        when:
        def env = environment([], [:])

        then:
        env.activeProfiles as List == []
        env.getProperty('server.port') == '8080'
        env.getProperty('spring.datasource.url') == "jdbc:h2:file:${System.getProperty('user.home')}/bbh-devsecops/dso-portal/dso-portal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"
        env.getProperty('spring.jpa.hibernate.ddl-auto') == 'none'
        env.getProperty('spring.liquibase.analytics-enabled') == 'false'
        env.getProperty('dso.demo-data') == null
        env.getProperty('management.endpoint.health.show-details') == 'always'
    }

    def "DSO_DATA_DIR moves the database files, for example onto the volume of the container"() {
        expect:
        environment([], [DSO_DATA_DIR: '/application/data']).getProperty('spring.datasource.url') ==
                'jdbc:h2:file:/application/data/dso-portal;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE'
    }

    def "the portal stays on its H2 file whatever profile is switched on: no profile connects it to Oracle"() {
        expect:
        environment([profile], ORACLE).getProperty('spring.datasource.url').startsWith('jdbc:h2:file:')

        where:
        profile << ['rd', 'qc', 'prod', 'oracle']
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
