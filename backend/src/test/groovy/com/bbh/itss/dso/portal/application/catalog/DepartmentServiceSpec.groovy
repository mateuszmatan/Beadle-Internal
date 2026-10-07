package com.bbh.itss.dso.portal.application.catalog

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary
import com.bbh.itss.dso.portal.domain.catalog.Department
import org.spockframework.mock.EmptyOrDummyResponse
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION

class DepartmentServiceSpec extends Specification {

    static final Department AI_LAB = new Department(1L, 'AI Lab', 0)
    static final Department CORPORATE = new Department(3L, 'corporate Technology', 1)
    static final Department FUND_SERVICES = new Department(5L, 'Fund Services', 0)

    DepartmentRepositoryPort departments = Mock(defaultResponse: EmptyOrDummyResponse.INSTANCE)
    ProductRepositoryPort products = Stub()
    PipelineCountsPort pipelineCounts = Stub()
    def service = new DepartmentService(departments, products, pipelineCounts)

    def setup() {
        products.summaries() >> [summary(1, 3L), summary(2, 3L), summary(3, 5L), summary(4, null)]
        products.servicesPerProduct() >> [1L: 2L, 2L: 1L, 3L: 4L, 4L: 9L]
        pipelineCounts.pipelinesPerProduct() >> [1L: 7L, 3L: 6L, 4L: 9L]
        pipelineCounts.activePipelinesPerProduct() >> [1L: 7L, 3L: 5L, 4L: 9L]
    }

    def "departments are listed by name in any case with the totals of their products, services and pipelines"() {
        given:
        departments.findAll() >> [FUND_SERVICES, AI_LAB, CORPORATE, new Department(2L, 'Capital Partners', 0)]

        expect:
        service.list() == [new DepartmentView(1, 'AI Lab', 0, 0, 0, 0, 0),
                           new DepartmentView(2, 'Capital Partners', 0, 0, 0, 0, 0),
                           new DepartmentView(3, 'corporate Technology', 1, 2, 3, 7, 7),
                           new DepartmentView(5, 'Fund Services', 0, 1, 4, 6, 5)]
    }

    def "a new department is stored under its trimmed name and shown without products"() {
        when:
        def created = service.create(' Custody ')

        then:
        1 * departments.save(new Department(null, 'Custody', 0)) >> new Department(4L, 'Custody', 0)
        departments.findAll() >> [AI_LAB, new Department(4L, 'Custody', 0)]
        created == new DepartmentView(4, 'Custody', 0, 0, 0, 0, 0)
    }

    def "a renamed department is shown with its totals"() {
        given:
        def stored = new Department(3L, 'Corporate Technology', 2)
        departments.load(3L) >> Optional.of(CORPORATE)

        when:
        def renamed = service.rename(3L, 1L, 'Corporate Technology')

        then:
        1 * departments.save(new Department(3L, 'Corporate Technology', 1)) >> stored
        departments.findAll() >> [stored]
        renamed == new DepartmentView(3, 'Corporate Technology', 2, 2, 3, 7, 7)
    }

    def "a department without products is deleted"() {
        given:
        departments.findAll() >> [AI_LAB, CORPORATE]

        when:
        service.delete(1L)

        then:
        1 * departments.delete(1L)
    }

    def "#refusal is refused before anything is stored"() {
        given:
        departments.findAll() >> [AI_LAB, CORPORATE, FUND_SERVICES]
        departments.load(3L) >> Optional.of(CORPORATE)
        departments.findByName('fund services') >> Optional.of(FUND_SERVICES)

        when:
        action(service)

        then:
        def e = thrown(IllegalStateException)
        e.message == message
        0 * departments.save(_)
        0 * departments.delete(_)

        where:
        refusal                               | action                                        || message
        'a new name in use'                   | { it.create('fund services') }                || 'A department named Fund Services already exists'
        'a rename to a name in use'           | { it.rename(3L, 1L, 'fund services') }        || 'A department named Fund Services already exists'
        'a rename of an old version'          | { it.rename(3L, 0L, 'Corporate Technology') } || STALE_VERSION
        'deleting a department with products' | { it.delete(3L) }                             || 'corporate Technology still has 2 product(s). Move them to another department first.'
    }

    def "an unknown department is not found, renamed or deleted"() {
        when:
        action(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Department 9 does not exist'
        0 * departments.save(_)
        0 * departments.delete(_)

        where:
        action << [{ it.rename(9L, 0L, 'Custody') }, { it.delete(9L) }]
    }

    private static ProductSummary summary(long id, Long departmentId) {
        new ProductSummary(id, "P$id", "Product $id", null, null, departmentId, null, Instant.EPOCH)
    }
}
