package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class ApiExceptionHandlerSpec extends Specification {

    static final FieldProblem PROBLEM = new FieldProblem('services[0].build.javaPath', 'is required')
    static final String UNEXPECTED = "The portal could not handle the request. The failure is in the portal's log."

    def handler = new ApiExceptionHandler()

    MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(handler)
            .build()

    def "#failure is #status #title"() {
        expect:
        with(answer(handler)) {
            it.status == status
            it.title == title
            it.detail == detail
            it.properties?.errors == errors
        }

        where:
        failure                    | answer                                                 || status | title
        'a missing record'         | { it.notFound(NotFoundException.of('Product', 7)) }   || 404    | 'Not found'
        'a clash with stored data' | { it.conflict(new ConflictException('code clash')) }  || 409    | 'Conflict'
        'a concurrent change'      | { it.staleData(new ObjectOptimisticLockingFailureException(Object, 1L)) } || 409 | 'Conflict'
        'a broken business rule'   | { it.invalid(new InvalidRequestException([PROBLEM])) } || 400   | 'Validation failed'
        'an unexpected failure'    | { it.unexpected(new IllegalStateException('at com.bbh')) } || 500 | 'Request failed'

        detail << ['Product 7 does not exist', 'code clash', ConflictException.STALE_VERSION, 'is required', UNEXPECTED]
        errors << [null, null, null, [PROBLEM], null]
    }

    def "a violated database constraint is 409 without the constraint's name"() {
        when:
        def problem = handler.integrity(new DataIntegrityViolationException('UK_DSO_PRODUCT_CODE'))

        then:
        problem.status == 409
        problem.detail.contains('duplicate')
        !problem.detail.contains('UK_DSO_PRODUCT_CODE')
    }

    def "bean validation errors are listed per field"() {
        when:
        def response = mvc.perform(json(toJson([type: 'FULL', agentLabels: labels]))).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Validation failed'
            it.detail == detail
            errors*.field == fields
        }

        where:
        labels          || detail                   | fields
        []              || 'add at least one Jenkins agent label' | ['agentLabels']
        ['a', 'b c d?'] || "agent labels may contain letters, digits, '.', '-' and '_'" | ['agentLabels[1]']
    }

    def "#request answers #status #title without naming a class or a method"() {
        when:
        def response = mvc.perform(builder).andReturn().response

        then:
        response.status == status
        response.contentType.startsWith('application/problem+json')
        with(parse(response.contentAsString)) {
            it.title == title
            it.detail == detail
            errors == null
        }

        where:
        request                  | builder                                           || status | title                    | detail
        'a failing controller'   | get('/api/samples/7')                             || 500    | 'Request failed'         | UNEXPECTED
        'no body'                | json('')                                          || 400    | 'Malformed request'      | 'The request body is missing.'
        'a body that is no JSON' | json('{"type":')                                  || 400    | 'Malformed request'      | 'The request body is not valid JSON.'
        'a body of another shape'| json('["FULL"]')                                  || 400    | 'Malformed request'      | 'The request body does not have the shape this endpoint expects.'
        'a path value'           | get('/api/samples/undefined')                     || 400    | 'Malformed request'      | "The value given for 'id' is not one this endpoint can read."
        'a query value'          | get('/api/samples').param('size', 'big')          || 400    | 'Malformed request'      | "The value given for 'size' is not one this endpoint can read."
        'no query value'         | get('/api/samples')                               || 400    | 'Bad Request'            | "Required parameter 'size' is not present."
        'another method'         | post('/api/samples/7')                            || 405    | 'Method Not Allowed'     | "Method 'POST' is not supported."
        'another content type'   | post('/api/samples').contentType(MediaType.TEXT_PLAIN).content('FULL') || 415 | 'Unsupported Media Type' | "Content-Type 'text/plain' is not supported."
    }

    private static MockHttpServletRequestBuilder json(String body) {
        post('/api/samples').contentType(MediaType.APPLICATION_JSON).content(body)
    }

    @RestController
    static class SampleController {

        @GetMapping('/api/samples/{id}')
        String read(@PathVariable('id') Long id) {
            throw new IllegalStateException("no sample $id is loaded")
        }

        @GetMapping('/api/samples')
        String list(@RequestParam('size') int size) {
            "$size samples"
        }

        @PostMapping('/api/samples')
        String create(@jakarta.validation.Valid @RequestBody PipelineRequest request) {
            request.type().name()
        }
    }
}
