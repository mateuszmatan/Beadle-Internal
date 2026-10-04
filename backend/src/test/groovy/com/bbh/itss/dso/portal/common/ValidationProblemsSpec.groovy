package com.bbh.itss.dso.portal.common

import com.bbh.itss.dso.portal.common.InvalidRequestException.FieldProblem
import spock.lang.Specification

class ValidationProblemsSpec extends Specification {

    def problems = new ValidationProblems()

    def "a view prefixes the fields it reports with its path"() {
        when:
        problems.add('code', 'is taken')
        problems.at('services[0]').at('build').add('javaPath', 'is required')

        then:
        !problems.isEmpty()
        problems.list() == [new FieldProblem('code', 'is taken'),
                            new FieldProblem('services[0].build.javaPath', 'is required')]
    }

    def "a request without problems passes"() {
        when:
        problems.throwIfAny()

        then:
        problems.isEmpty()
        notThrown(InvalidRequestException)
    }

    def "a single problem is the message of the exception"() {
        given:
        problems.add('name', 'is taken')

        when:
        problems.throwIfAny()

        then:
        def e = thrown(InvalidRequestException)
        e.message == 'is taken'
        e.problems == [new FieldProblem('name', 'is taken')]
    }

    def "several problems are counted in the message of the exception"() {
        given:
        problems.add('name', 'is taken')
        problems.at('services[1]').add('name', 'is not unique')

        when:
        problems.throwIfAny()

        then:
        def e = thrown(InvalidRequestException)
        e.message == '2 fields are invalid'
        e.problems*.field == ['name', 'services[1].name']
    }

    def "the list is a snapshot that cannot be changed"() {
        given:
        problems.add('name', 'is taken')
        def snapshot = problems.list()

        when:
        problems.add('code', 'is taken')

        then:
        snapshot.size() == 1

        when:
        snapshot.add(new FieldProblem('x', 'y'))

        then:
        thrown(UnsupportedOperationException)
    }

    def "a single field problem can be raised directly"() {
        when:
        def e = InvalidRequestException.of('range', 'use 30d')

        then:
        e.message == 'use 30d'
        e.problems == [new FieldProblem('range', 'use 30d')]
    }
}
