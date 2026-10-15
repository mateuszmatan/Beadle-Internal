package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.jira.port.in.JiraUseCase
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import java.time.LocalDate

import static com.bbh.itss.beadle.internal.support.Fixtures.issue
import static com.bbh.itss.beadle.internal.support.Fixtures.version
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

class JiraControllerSpec extends ControllerSpecification {

    JiraUseCase jira = Mock()

    @Override
    Object controller() {
        new JiraController(jira)
    }

    def "the versions of a project are listed with their release dates"() {
        when:
        def response = call(get('/api/internal/jira/projects/CERT/versions'))

        then:
        1 * jira.versions('CERT') >> [version(), version(name: 'CERT 1.3', released: true, releaseDate: LocalDate.parse('2026-09-01')),
                                      version(name: 'CERT 1.5', releaseDate: null)]
        response.status == 200
        bodyOf(response) == [[name: 'CERT 1.4', released: false, releaseDate: '2026-11-01'],
                             [name: 'CERT 1.3', released: true, releaseDate: '2026-09-01'],
                             [name: 'CERT 1.5', released: false, releaseDate: null]]
    }

    def "the epics of a FixVersion are listed with their key, summary, status and change date"() {
        when:
        def response = call(get('/api/internal/jira/projects/CERT/epics').param('fixVersion', 'CERT 1.4'))

        then:
        1 * jira.epics('CERT', 'CERT 1.4') >> [issue()]
        response.status == 200
        bodyOf(response) == [[key: 'CERT-12', summary: 'Single sign-on with BBH SSO', status: 'In Progress', epicKey: null,
                              updated: '2026-10-01']]
    }

    def "the stories of chosen epics are listed with their epic key, from #epicKeys"() {
        when:
        def response = call(get('/api/internal/jira/projects/CERT/stories').param('fixVersion', 'CERT 1.4')
                .param('epicKeys', epicKeys as String[]))

        then:
        1 * jira.stories('CERT', 'CERT 1.4', ['CERT-12', 'CERT-13']) >> [issue(key: 'CERT-20', epicKey: 'CERT-12', updated: null)]
        response.status == 200
        bodyOf(response) == [[key: 'CERT-20', summary: 'Single sign-on with BBH SSO', status: 'In Progress', epicKey: 'CERT-12',
                              updated: null]]

        where:
        epicKeys << [['CERT-12,CERT-13'], ['CERT-12', 'CERT-13']]
    }

    def "#request without a FixVersion is refused with the problem"() {
        when:
        def response = call(get(url))

        then:
        1 * jira._(*_) >> { throw InvalidRequestException.of('fixVersion', 'is required') }
        response.status == 400
        response.contentType.startsWith('application/problem+json')
        bodyOf(response) == [title: 'Validation failed', status: 400, detail: 'is required',
                             instance: url.tokenize('?')[0], problems: [[field: 'fixVersion', message: 'is required']]]

        where:
        request       | url
        'the epics'   | '/api/internal/jira/projects/CERT/epics'
        'the stories' | '/api/internal/jira/projects/CERT/stories?epicKeys=CERT-12'
    }

    def "an unknown project is 404 and an unreachable Jira is 502"() {
        when:
        def missing = call(get('/api/internal/jira/projects/NOPE/versions'))
        def down = call(get('/api/internal/jira/projects/CERT/versions'))

        then:
        1 * jira.versions('NOPE') >> { throw new NoSuchElementException('Jira project NOPE does not exist') }
        1 * jira.versions('CERT') >> { throw new UncheckedIOException('Jira could not read the versions of CERT: 503', new IOException()) }
        missing.status == 404
        bodyOf(missing).detail == 'Jira project NOPE does not exist'
        down.status == 502
        bodyOf(down).title == 'Bad Gateway'
        bodyOf(down).detail == 'Jira could not read the versions of CERT: 503'
    }

    def "without the service token #path is 401 (#authorization)"() {
        when:
        def response = callWithout(get('/api/internal/jira/projects/CERT/' + path), authorization)

        then:
        response.status == 401
        bodyOf(response).detail == 'The call needs the Beadle service token as Authorization: Bearer <token>.'
        0 * jira._

        where:
        [path, authorization] << [['versions', 'epics', 'stories'], unauthorized()].combinations()
    }
}
