package com.bbh.itss.dso.portal.adapter.in.web

import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import spock.lang.Specification
import spock.lang.Subject

class ApiExceptionHandlerSpec extends Specification {

    @Subject
    def handler = new ApiExceptionHandler()

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

    def "an unreadable body is 400 naming the cause"() {
        given:
        def cause = new IllegalArgumentException('Unexpected character')

        when:
        def problem = handler.unreadable(new HttpMessageNotReadableException('JSON parse error', cause,
                new MockHttpInputMessage(new byte[0])))

        then:
        problem.status == 400
        problem.title == 'Malformed request'
        problem.detail == 'The request body could not be read: Unexpected character'
    }

    def "bean validation errors are listed per field"() {
        given:
        def binding = new BeanPropertyBindingResult(new Object(), 'request')
        errors.each { binding.addError(new FieldError('request', it.key, it.value)) }
        def parameter = new MethodParameter(String.getMethod('concat', String), 0)

        when:
        def problem = handler.beanValidation(new MethodArgumentNotValidException(parameter, binding))

        then:
        problem.status == 400
        problem.detail == detail
        problem.properties.errors == errors.collect { new FieldProblem(it.key, it.value) }

        where:
        errors                                                   || detail
        [code: 'must not be blank']                              || 'must not be blank'
        [code: 'must not be blank', 'services[0].name': 'empty'] || '2 fields are invalid'
    }
}
