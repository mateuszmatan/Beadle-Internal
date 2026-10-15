package com.bbh.itss.beadle.internal.config

import com.bbh.itss.beadle.internal.application.calllog.CallerContext
import com.bbh.itss.beadle.internal.domain.calllog.Caller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.config.ServiceTokenFilter.CORRELATION_TOO_LONG
import static com.bbh.itss.beadle.internal.config.ServiceTokenFilter.DETAIL
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.CORRELATION_MAX
import static com.bbh.itss.beadle.internal.domain.calllog.CallLogEntry.USER_MAX
import static com.bbh.itss.beadle.internal.support.Json.parse
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static java.nio.charset.StandardCharsets.UTF_8
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

    def "an accepted call runs as the user and correlation id of its headers, cleared afterwards"() {
        when:
        def response = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)
                .header('X-Beadle-User', ' Mateusz Matan ').header('X-Correlation-Id', 'corr-1')).andReturn().response

        then:
        response.contentAsString == 'Mateusz Matan/corr-1'
        CallerContext.current() == Caller.NONE
    }

    def "a call without the headers gets a fresh correlation id and an unknown user"() {
        when:
        def first = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)).andReturn().response
        def second = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)
                .header('X-Beadle-User', ' ').header('X-Correlation-Id', '')).andReturn().response

        then:
        first.contentAsString ==~ /unknown\/[0-9a-f-]{36}/
        second.contentAsString ==~ /unknown\/[0-9a-f-]{36}/
        first.contentAsString != second.contentAsString
    }

    def "the user travels percent-encoded in UTF-8 and is logged decoded: #header"() {
        when:
        def response = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)
                .header('X-Beadle-User', header).header('X-Correlation-Id', 'corr-1')).andReturn().response

        then:
        response.status == 200
        response.contentAsString == user + '/corr-1'
        CallerContext.current() == Caller.NONE

        where:
        header                                                || user
        '%C5%81ukasz+W%C3%B3jcik'                             || 'Łukasz Wójcik'
        '%C5%81ukasz%20W%C3%B3jcik'                           || 'Łukasz Wójcik'
        URLEncoder.encode('Żaneta Ćwięk-Ślusarczyk', 'UTF-8') || 'Żaneta Ćwięk-Ślusarczyk'
        'Mateusz Matan'                                       || 'Mateusz Matan'
        'Mateusz+Matan'                                       || 'Mateusz Matan'
        ' Mateusz+Matan '                                     || 'Mateusz Matan'
        '+Mateusz+Matan+'                                     || 'Mateusz Matan'
        'system'                                              || 'system'
        '100% sure'                                           || '100% sure'
        ' 100% sure '                                         || '100% sure'
        '%C5%8'                                               || '%C5%8'
        '%20'                                                 || 'unknown'
        '+++'                                                 || 'unknown'
    }

    def "a user of #user.length() characters (#header.length() in the header) is cut once decoded to the 200 bytes"() {
        when:
        def response = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)
                .header('X-Beadle-User', header).header('X-Correlation-Id', 'corr-1')).andReturn().response
        def caller = response.contentAsString - '/corr-1'

        then:
        response.status == 200
        caller == expected
        caller.getBytes(UTF_8).length <= USER_MAX

        where:
        user           | header                                || expected
        'u' * USER_MAX | 'u' * USER_MAX                        || 'u' * USER_MAX
        'u' * 250      | 'u' * 250                             || 'u' * (USER_MAX - 3) + '...'
        'Ł' * 100      | URLEncoder.encode('Ł' * 100, 'UTF-8') || 'Ł' * 100
        'Ł' * 150      | URLEncoder.encode('Ł' * 150, 'UTF-8') || 'Ł' * 98 + '...'
    }

    def "a correlation id of #length bytes is #outcome"() {
        when:
        def response = mvc.perform(get('/api/internal/caller').header('Authorization', 'Bearer ' + TOKEN)
                .header('X-Beadle-User', 'Mateusz Matan').header('X-Correlation-Id', 'c' * length)).andReturn().response

        then:
        response.status == status
        if (status == 200) {
            assert response.contentAsString == 'Mateusz Matan/' + 'c' * length
        } else {
            assert response.contentType == 'application/problem+json;charset=UTF-8'
            assert parse(response.contentAsString) == [title   : 'Validation failed', status: 400,
                                                      detail  : 'X-Correlation-Id ' + CORRELATION_TOO_LONG,
                                                      problems: [[field: 'X-Correlation-Id', message: CORRELATION_TOO_LONG]]]
        }
        CallerContext.current() == Caller.NONE

        where:
        length              || status | outcome
        CORRELATION_MAX     || 200    | 'taken as it is'
        CORRELATION_MAX + 1 || 400    | 'refused, since cutting it would lose the callbacks of the action'
    }

    def "a call without the service token is refused before its headers are looked at"() {
        expect:
        mvc.perform(get('/api/internal/caller').header('X-Correlation-Id', 'c' * 120)).andReturn().response.status == 401
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

        @GetMapping(value = '/api/internal/caller', produces = 'text/plain;charset=UTF-8')
        String caller() {
            def caller = CallerContext.current()
            "${caller.user()}/${caller.correlationId()}"
        }
    }
}
