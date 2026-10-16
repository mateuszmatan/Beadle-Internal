package com.bbh.itss.beadle.internal.adapter.in.web

import com.bbh.itss.beadle.internal.application.confluence.port.in.ConfluenceUseCase
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.internal.support.ControllerSpecification

import static com.bbh.itss.beadle.internal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.internal.domain.shared.Failures.staleVersion
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.newPageJson
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdateJson
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

class ConfluenceControllerSpec extends ControllerSpecification {

    static final Map PAGE = [id  : '100001', spaceKey: 'DSO', title: 'Beadle release notes', version: 3,
                             url : 'https://confluence.bbh.com/pages/viewpage.action?pageId=100001',
                             body: '<p>Beadle 1.0</p>']

    ConfluenceUseCase confluence = Mock()

    @Override
    Object controller() {
        new ConfluenceController(confluence)
    }

    def "pages are found by space and title and listed without their body"() {
        when:
        def response = call(get('/api/internal/confluence/pages').param('spaceKey', 'DSO').param('title', 'Beadle release notes'))

        then:
        1 * confluence.find('DSO', 'Beadle release notes') >> [page(body: null), page(id: '100002', url: null, body: null)]
        response.status == 200
        bodyOf(response) == [PAGE - [body: PAGE.body], PAGE - [body: PAGE.body] + [id: '100002', url: null]]
    }

    def "a search without its parameters is refused with the problems"() {
        when:
        def response = call(get('/api/internal/confluence/pages'))

        then:
        1 * confluence.find('', '') >> { throw new InvalidRequestException([new InvalidRequestException.FieldProblem('spaceKey', 'is required'),
                                                                                 new InvalidRequestException.FieldProblem('title', 'is required')]) }
        response.status == 400
        bodyOf(response).problems*.field == ['spaceKey', 'title']
    }

    def "a page is read with its body"() {
        when:
        def response = call(get('/api/internal/confluence/pages/100001'))

        then:
        1 * confluence.get('100001') >> page()
        response.status == 200
        bodyOf(response) == PAGE
    }

    def "a page is created from the request and answered with its id"() {
        when:
        def response = call(json(post('/api/internal/confluence/pages'), newPageJson(spaceKey: ' DSO ', parentId: parentId)))

        then:
        1 * confluence.create(newPage(parentId: parentId)) >> page(id: '100009', title: 'Change CHG0031001', version: 1, body: '<p>Evidence</p>')
        response.status == 201
        response.getHeader('Location') == 'http://localhost/api/internal/confluence/pages/100009'
        bodyOf(response) == PAGE + [id: '100009', title: 'Change CHG0031001', version: 1, body: '<p>Evidence</p>']

        where:
        parentId << ['100001', null]
    }

    def "a page is updated from the request with the version it replaces"() {
        when:
        def response = call(json(put('/api/internal/confluence/pages/100001'), pageUpdateJson()))

        then:
        1 * confluence.update('100001', pageUpdate()) >> page(version: 4, body: '<p>Beadle 1.1</p>')
        response.status == 200
        bodyOf(response) == PAGE + [version: 4, body: '<p>Beadle 1.1</p>']
    }

    def "a page request with #problem is refused naming the fields"() {
        when:
        def response = call(json(request, body))

        then:
        0 * confluence._
        response.status == 400
        with(bodyOf(response)) {
            title == 'Validation failed'
            problems*.field == fields
        }

        where:
        problem                   | request                                       | body                                       || fields
        'no space and no body'    | post('/api/internal/confluence/pages')        | newPageJson(spaceKey: null, body: ' ')     || ['spaceKey', 'body']
        'a title of 256 bytes'    | post('/api/internal/confluence/pages')        | newPageJson(title: 'x' * 256)              || ['title']
        'no version'              | put('/api/internal/confluence/pages/100001')  | pageUpdateJson(version: null)              || ['version']
        'version 0 and no title'  | put('/api/internal/confluence/pages/100001')  | pageUpdateJson(version: 0, title: '')      || ['title', 'version']
    }

    def "an unknown page is 404, a stale update 409 and a refusing Confluence 502"() {
        when:
        def missing = call(get('/api/internal/confluence/pages/1'))
        def stale = call(json(put('/api/internal/confluence/pages/100001'), pageUpdateJson(version: 2)))
        def refused = call(json(post('/api/internal/confluence/pages'), newPageJson()))

        then:
        1 * confluence.get('1') >> { throw new NoSuchElementException('Confluence page 1 does not exist') }
        1 * confluence.update('100001', pageUpdate(version: 2)) >> { throw staleVersion() }
        1 * confluence.create(newPage()) >> { throw new UncheckedIOException('Confluence could not create the page Change CHG0031001 in space DSO: 400 Bad Request', new IOException()) }
        missing.status == 404
        bodyOf(missing).detail == 'Confluence page 1 does not exist'
        stale.status == 409
        bodyOf(stale).detail == STALE_VERSION
        refused.status == 502
        bodyOf(refused).detail.startsWith('Confluence could not create the page')
    }

    def "without the service token #request is 401 (#authorization)"() {
        when:
        def response = callWithout(builder(), authorization)

        then:
        response.status == 401
        bodyOf(response).title == 'Unauthorized'
        0 * confluence._

        where:
        [request, builder, authorization] << [[['finding', { get('/api/internal/confluence/pages?spaceKey=DSO&title=x') }],
                                               ['reading', { get('/api/internal/confluence/pages/100001') }],
                                               ['creating', { json(post('/api/internal/confluence/pages'), newPageJson()) }],
                                               ['updating', { json(put('/api/internal/confluence/pages/100001'), pageUpdateJson()) }]],
                                              unauthorized()].combinations().collect { it[0] + [it[1]] }
    }
}
