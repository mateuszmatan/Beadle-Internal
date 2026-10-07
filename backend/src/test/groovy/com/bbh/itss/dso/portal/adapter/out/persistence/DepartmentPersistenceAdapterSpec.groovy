package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Department
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:department-adapter;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DepartmentPersistenceAdapter)
class DepartmentPersistenceAdapterSpec extends Specification {

    @Autowired
    DepartmentPersistenceAdapter adapter

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "the database starts with the five BBH departments and finds them by id and by name in any case"() {
        expect:
        adapter.findAll().sort(false) { it.id() } == [
                new Department(1L, 'AI Lab', 0), new Department(2L, 'Capital Partners', 0),
                new Department(3L, 'Corporate Technology', 0), new Department(4L, 'Custody', 0),
                new Department(5L, 'Fund Services', 0)]
        adapter.load(3L) == Optional.of(new Department(3L, 'Corporate Technology', 0))
        adapter.load(9999L) == Optional.empty()
        adapter.findByName('fund SERVICES') == Optional.of(new Department(5L, 'Fund Services', 0))
        adapter.findByName('Treasury') == Optional.empty()
    }

    def "a department is created, renamed to a new version and deleted"() {
        when:
        def created = adapter.save(Department.create('Treasury', { adapter.findByName(it) }))
        entities.clear()
        def renamed = adapter.save(adapter.load(created.id()).get().rename(0L, 'Treasury Services',
                { adapter.findByName(it) }))
        entities.clear()

        then:
        created.id() > 5
        created.version() == 0
        renamed == new Department(created.id(), 'Treasury Services', 1)
        jdbc.queryForMap('SELECT NAME, VERSION FROM DSO_DEPARTMENT WHERE ID = ?', created.id()) ==
                [NAME: 'Treasury Services', VERSION: 1]

        when:
        adapter.delete(created.id())
        adapter.delete(9999L)
        entities.flush()

        then:
        adapter.load(created.id()) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_DEPARTMENT', Integer) == 5
    }

    def "a department #change is not written"() {
        when:
        adapter.save(department)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        jdbc.queryForObject('SELECT NAME FROM DSO_DEPARTMENT WHERE ID = 4', String) == 'Custody'

        where:
        change                                | department
        'read at another version than stored' | new Department(4L, 'Stale', 3)
        'deleted in the meantime'             | new Department(404L, 'Gone', 0)
    }
}
