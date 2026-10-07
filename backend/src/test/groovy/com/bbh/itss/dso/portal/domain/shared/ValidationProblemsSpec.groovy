package com.bbh.itss.dso.portal.domain.shared

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

class ValidationProblemsSpec extends Specification {

    def problems = new ValidationProblems()

    def "a view prefixes the fields it reports with its path"() {
        when:
        problems.add('code', 'is taken')
        problems.at('services[0]').at('build').add('javaPath', 'is required')

        then:
        !problems.list().isEmpty()
        problems.list() == [new FieldProblem('code', 'is taken'),
                            new FieldProblem('services[0].build.javaPath', 'is required')]
    }

    def "a request with #fields.size() problems #outcome"() {
        given:
        fields.each { problems.add(it, 'is taken') }

        when:
        problems.throwIfAny()

        then:
        def e = thrown(InvalidRequestException)
        e.message == message
        e.problems()*.field == fields

        where:
        fields           || message
        ['name']         || 'is taken'
        ['name', 'code'] || '2 fields are invalid'

        outcome = fields.size() == 1 ? 'says it' : 'counts them'
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

    def "a request without problems passes and a single field problem can be raised directly"() {
        when:
        problems.throwIfAny()
        def e = InvalidRequestException.of('range', 'use 30d')

        then:
        problems.list().isEmpty()
        e.message == 'use 30d'
        e.problems() == [new FieldProblem('range', 'use 30d')]
    }
}
