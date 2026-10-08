package com.bbh.itss.dso.portal.domain.catalog

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.util.function.Function

import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION

class DepartmentSpec extends Specification {

    static final Department CUSTODY = new Department(4L, 'Custody', 2)

    static final Function<String, Optional<Department>> nobody = { Optional.empty() }
    static final Function<String, Optional<Department>> custody = { Optional.of(CUSTODY) }

    def "a new department keeps its name trimmed and starts at version 0"() {
        expect:
        Department.create('  AI Lab ', nobody) == new Department(null, 'AI Lab', 0)
        Department.create('D' * 100, nobody).name().length() == 100
        new Department(1L, null, 0).name() == null
    }

    def "a department name that is #problem is refused"() {
        when:
        Department.create(name, nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('name', message)]

        where:
        problem    | name      || message
        'missing'  | null      || 'must not be blank'
        'blank'    | '   '     || 'must not be blank'
        'too long' | 'D' * 101 || 'must be at most 100 characters'
    }

    def "a name another department uses, in any case, is refused"() {
        when:
        change()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'A department named Custody already exists'

        where:
        change << [{ Department.create(' custody ', custody) },
                   { new Department(5L, 'Fund Services', 0).rename(0L, 'CUSTODY', custody) }]
    }

    def "a rename keeps the id and the version and may change the case of the department's own name"() {
        expect:
        CUSTODY.rename(2L, ' CUSTODY ', custody) == new Department(4L, 'CUSTODY', 2)
        CUSTODY.rename(2L, 'Custody and Trust', nobody) == new Department(4L, 'Custody and Trust', 2)
    }

    def "a rename #based is refused as stale before the name is checked"() {
        when:
        CUSTODY.rename(version, ' ', nobody)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION

        where:
        based                       | version
        'on an older version'       | 1L
        'on a version it never had' | 3L
        'without a version'         | null
    }
}
