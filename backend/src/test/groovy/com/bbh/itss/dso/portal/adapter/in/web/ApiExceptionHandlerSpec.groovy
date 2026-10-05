package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.support.ApiJson.parse
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.ApiJson.toJson
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class ApiExceptionHandlerSpec extends Specification {

    @Subject
    def handler = new ApiExceptionHandler()

    MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(handler)
            .build()

    def "a missing record is 404"() {
        when:
        def problem = handler.notFound(NotFoundException.of('Product', 7))

        then:
        problem.status == HttpStatus.NOT_FOUND.value()
        problem.title == 'Not found'
        problem.detail == 'Product 7 does not exist'
    }

    def "a clash with stored data is 409"() {
        when:
        def problem = handler.conflict(new ConflictException('Product code CERT is already used'))

        then:
        problem.status == 409
        problem.title == 'Conflict'
        problem.detail == 'Product code CERT is already used'
    }

    def "a concurrent change is 409 with advice to reload"() {
        when:
        def problem = handler.staleData(new ObjectOptimisticLockingFailureException(Object, 1L))

        then:
        problem.status == 409
        problem.detail.contains('changed by someone else')
    }

    def "a violated database constraint is 409"() {
        when:
        def problem = handler.integrity(new DataIntegrityViolationException('UK_DSO_PRODUCT_CODE'))

        then:
        problem.status == 409
        problem.detail.contains('duplicate')
        !problem.detail.contains('UK_DSO_PRODUCT_CODE')
    }

    def "business rule violations are 400 with the field problems"() {
        given:
        def problems = [new FieldProblem('services[0].build.javaPath', 'is required')]

        when:
        def problem = handler.invalid(new InvalidRequestException(problems))

        then:
        problem.status == 400
        problem.title == 'Validation failed'
        problem.detail == 'is required'
        problem.properties.errors == problems
    }

    def "an unexpected failure is a 500 problem that keeps the cause in the log"() {
        when:
        def problem = handler.unexpected(new IllegalStateException('the pool is exhausted at com.bbh.itss.dso'))

        then:
        problem.status == 500
        problem.title == 'Request failed'
        problem.detail == 'The portal could not handle the request. The failure is in the portal\'s log.'
    }

    def "a failure in a controller answers a problem instead of the servlet error page"() {
        when:
        def response = mvc.perform(get('/api/samples/7')).andReturn().response

        then:
        response.status == 500
        response.contentType.startsWith('application/problem+json')
        with(parse(response.contentAsString)) {
            title == 'Request failed'
            detail == 'The portal could not handle the request. The failure is in the portal\'s log.'
            !detail.contains('IllegalStateException')
        }
    }

    def "bean validation errors are listed per field"() {
        when:
        def response = mvc.perform(post('/api/samples').contentType(MediaType.APPLICATION_JSON)
                .content(toJson([type: 'FULL', agentLabels: labels]))).andReturn().response

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

    def "a value the request body cannot hold names the field and the values allowed"() {
        when:
        def response = mvc.perform(post('/api/samples').contentType(MediaType.APPLICATION_JSON)
                .content('{"type": "ALMOST_FULL", "agentLabels": ["linux-agent"]}')).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Malformed request'
            detail == 'type must be one of FULL, SECURITY, EXTENDED, SAST'
            errors == [[field: 'type', message: 'must be one of FULL, SECURITY, EXTENDED, SAST']]
        }
    }

    def "a value of the wrong type names the field it belongs to, however deep"() {
        when:
        def response = mvc.perform(post('/api/samples/products').contentType(MediaType.APPLICATION_JSON)
                .content(toJson(product(services: [service(build: [tool: 'GRADLE', command: [tasks: 'clean build']])]))))
                .andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Malformed request'
            detail == 'services[0].build.command.tasks has a value this field cannot hold'
            errors == [[field  : 'services[0].build.command.tasks',
                        message: 'has a value this field cannot hold']]
        }
    }

    def "a body that is not JSON at all says so without naming a class or a method"() {
        when:
        def response = mvc.perform(post('/api/samples').contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Malformed request'
            it.detail == detail
            errors == null
        }

        where:
        body              || detail
        ''                || 'The request body is missing.'
        '{"type":'        || 'The request body is not valid JSON.'
        '["FULL"]'        || 'The request body does not have the shape this endpoint expects.'
    }

    def "a path value of the wrong type is a problem that names the path variable"() {
        when:
        def response = mvc.perform(get('/api/samples/undefined')).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Malformed request'
            detail == "The value given for 'id' is not one this endpoint can read."
        }
    }

    def "a query value of the wrong type names the parameter"() {
        when:
        def response = mvc.perform(get('/api/samples').param('size', 'big')).andReturn().response

        then:
        response.status == 400
        parse(response.contentAsString).detail == "The value given for 'size' is not one this endpoint can read."
    }

    def "a missing query parameter is a problem detail"() {
        when:
        def response = mvc.perform(get('/api/samples')).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Bad Request'
            detail == "Required parameter 'size' is not present."
        }
    }

    def "a method or a content type the endpoint does not serve is a problem detail"() {
        when:
        def wrongMethod = mvc.perform(post('/api/samples/7')).andReturn().response
        def wrongType = mvc.perform(post('/api/samples').contentType(MediaType.TEXT_PLAIN).content('FULL'))
                .andReturn().response

        then:
        wrongMethod.status == 405
        with(parse(wrongMethod.contentAsString)) {
            title == 'Method Not Allowed'
            detail == "Method 'POST' is not supported."
        }
        wrongType.status == 415
        parse(wrongType.contentAsString).title == 'Unsupported Media Type'
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

        @PostMapping('/api/samples/products')
        String createProduct(@RequestBody ProductDto request) {
            request.code()
        }
    }
}
