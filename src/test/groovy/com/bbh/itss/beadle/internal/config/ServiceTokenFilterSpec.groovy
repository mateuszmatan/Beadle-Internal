package com.bbh.itss.beadle.internal.config

import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.config.ServiceTokenFilter.DETAIL
import static com.bbh.itss.beadle.internal.support.Json.parse
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ServiceTokenFilterSpec extends Specification {

    static final String TOKEN = 'shared-secret'

    MockMvc mvc = mvc(TOKEN)

    def "a call with the service token passes, any other is 401 as a problem"() {
        expect:
        mvc.perform(get('/api/internal/status').header('Authorization', 'Bearer ' + TOKEN)).andReturn().response.status == 200
        mvc.perform(get('/api/internal/status').header('Authorization', 'Bearer  ' + TOKEN + ' ')).andReturn().response.status == 200

        when:
        def response = mvc.perform(authorization == null ? get(path) : get(path).header('Authorization', authorization))
                .andReturn().response

        then:
        response.status == 401
        response.contentType == 'application/problem+json;charset=UTF-8'
        response.getHeader('WWW-Authenticate') == 'Bearer'
        parse(response.contentAsString) == [title: 'Unauthorized', status: 401, detail: DETAIL]

        where:
        path                     | authorization
        '/api/internal/status'   | null
        '/api/internal/status'   | 'Bearer wrong'
        '/api/internal/status'   | 'Bearer'
        '/api/internal/status'   | TOKEN
        '/api/internal/status'   | 'Basic c2hhcmVkLXNlY3JldA=='
        '/actuator/info'         | null
        '/actuator/healthcheck'  | null
        '/anything'              | 'bearer ' + TOKEN
    }

    def "the health endpoints are open"() {
        expect:
        mvc.perform(get(path)).andReturn().response.status == 200

        where:
        path << ['/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness']
    }

    def "a blank service token refuses every call rather than opening the API"() {
        expect:
        mvc(token).perform(get('/api/internal/status').header('Authorization', 'Bearer ' + token))
                .andReturn().response.status == 401
        mvc(token).perform(get('/api/internal/status').header('Authorization', 'Bearer ')).andReturn().response.status == 401
        mvc(token).perform(get('/actuator/health')).andReturn().response.status == 200

        where:
        token << [null, '', '  ']
    }

    private static MockMvc mvc(String token) {
        standaloneSetup(new OpenController()).addFilters(new ServiceTokenFilter(new ServiceTokenProperties(token))).build()
    }

    @RestController
    static class OpenController {

        @GetMapping(['/api/internal/status', '/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness',
                     '/actuator/info', '/actuator/healthcheck', '/anything'])
        String answer() {
            'ok'
        }
    }
}
