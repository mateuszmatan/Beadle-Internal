package com.bbh.itss.beadle.internal.adapter.out.confluence

import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage
import com.bbh.itss.beadle.internal.domain.shared.RefusedException
import groovy.json.JsonSlurper
import org.springframework.http.client.ClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.web.client.RestClient
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.adapter.out.confluence.ConfluenceAdapter.NO_CONTENT
import static com.bbh.itss.beadle.internal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate
import static org.springframework.http.HttpMethod.GET
import static org.springframework.http.HttpMethod.POST
import static org.springframework.http.HttpMethod.PUT
import static org.springframework.http.HttpStatus.CONFLICT
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class ConfluenceAdapterSpec extends Specification {

    static final String URL = 'https://confluence.bbh.com'
    static final String CONTENT = URL + '/rest/api/content'
    static final String NOTES = '''{"id":"100001","type":"page","title":"Beadle release notes","space":{"key":"DSO"},
        "version":{"number":3},"body":{"storage":{"value":"<p>Beadle 1.0</p>","representation":"storage"}},
        "_links":{"webui":"/display/DSO/Beadle+release+notes","self":"https://confluence.bbh.com/rest/api/content/100001"}}'''

    def builder = RestClient.builder()
    def server = MockRestServiceServer.bindTo(builder).build()
    def confluence = new ConfluenceAdapter(new ConfluenceProperties(URL + '/', 'secret-token'), builder)

    def "pages are found by space and title, with their version and link"() {
        given:
        server.expect(requestToUriTemplate(CONTENT + '?type=page&spaceKey={s}&title={t}&expand=space,version&limit=50',
                'DSO', 'Beadle release notes'))
                .andExpect(method(GET)).andExpect(header('Authorization', 'Bearer secret-token'))
                .andExpect(queryParam('title', 'Beadle%20release%20notes'))
                .andRespond(withSuccess('{"results":[' + NOTES + ',{"id":"100002","title":"Other","space":{"key":"DSO"},"version":{"number":1}}],' +
                        '"start":0,"limit":50,"size":2}', APPLICATION_JSON))

        when:
        def pages = confluence.find('DSO', 'Beadle release notes')

        then:
        confluence.connected()
        pages == [page(url: URL + '/display/DSO/Beadle+release+notes'),
                  new ConfluencePage('100002', 'DSO', 'Other', 1, null, null)]
        server.verify()
    }

    def "a page is read with its body in storage format"() {
        given:
        server.expect(requestTo(CONTENT + '/100001?expand=space,version,body.storage')).andExpect(method(GET))
                .andRespond(withSuccess(NOTES, APPLICATION_JSON))

        expect:
        confluence.get('100001') == page(url: URL + '/display/DSO/Beadle+release+notes')
    }

    def "a page is created under its parent, if any, in storage format"() {
        given:
        server.expect(requestTo(CONTENT + '?expand=space,version,body.storage')).andExpect(method(POST))
                .andExpect(body([type: 'page', title: 'Change CHG0031001', space: [key: 'DSO'],
                                 ancestors: [[id: '100001']],
                                 body: [storage: [value: '<p>Evidence</p>', representation: 'storage']]]))
                .andRespond(withSuccess('''{"id":"100009","type":"page","title":"Change CHG0031001","space":{"key":"DSO"},
                    "version":{"number":1},"body":{"storage":{"value":"<p>Evidence</p>"}},"_links":{"webui":"/display/DSO/Change+CHG0031001"}}''',
                        APPLICATION_JSON))
        server.expect(requestTo(CONTENT + '?expand=space,version,body.storage')).andExpect(method(POST))
                .andExpect(body([type: 'page', title: 'Top', space: [key: 'DSO'],
                                 body: [storage: [value: '<p/>', representation: 'storage']]]))
                .andRespond(withSuccess('{"id":"100010","title":"Top","space":{"key":"DSO"},"version":{"number":1}}', APPLICATION_JSON))

        expect:
        confluence.create(newPage()) == new ConfluencePage('100009', 'DSO', 'Change CHG0031001', 1,
                URL + '/display/DSO/Change+CHG0031001', '<p>Evidence</p>')
        confluence.create(newPage(title: 'Top', parentId: null, body: '<p/>')) ==
                new ConfluencePage('100010', 'DSO', 'Top', 1, null, null)
    }

    def "an update replaces the named version with the next one"() {
        given:
        server.expect(requestTo(CONTENT + '/100001?expand=space,version,body.storage')).andExpect(method(PUT))
                .andExpect(body([id: '100001', type: 'page', title: 'Beadle release notes', version: [number: 4],
                                 body: [storage: [value: '<p>Beadle 1.1</p>', representation: 'storage']]]))
                .andRespond(withSuccess(NOTES.replace('"number":3', '"number":4').replace('Beadle 1.0', 'Beadle 1.1'),
                        APPLICATION_JSON))

        expect:
        confluence.update('100001', pageUpdate()) ==
                page(version: 4, body: '<p>Beadle 1.1</p>', url: URL + '/display/DSO/Beadle+release+notes')
    }

    def "an update of a stale version is refused as such"() {
        given:
        server.expect(requestTo(CONTENT + '/100001?expand=space,version,body.storage')).andExpect(method(PUT))
                .andRespond(withStatus(CONFLICT).body('{"message":"Version must be incremented on update."}'))

        when:
        confluence.update('100001', pageUpdate(version: 2))

        then:
        def stale = thrown(IllegalStateException)
        stale.message == STALE_VERSION
    }

    def "an unknown page is not found when #operation"() {
        given:
        server.expect(requestTo(CONTENT + '/1?expand=space,version,body.storage')).andRespond(withResourceNotFound())

        when:
        call(confluence)

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'Confluence page 1 does not exist'

        where:
        operation  | call
        'reading'  | ({ ConfluenceAdapter it -> it.get('1') })
        'updating' | ({ ConfluenceAdapter it -> it.update('1', pageUpdate()) })
    }

    def "a Confluence refusal to #operation is reported with its reason"() {
        given:
        server.expect(requestToUriTemplate(url)).andRespond(response)

        when:
        call(confluence)

        then:
        def refused = thrown(type)
        refused.message.startsWith("Confluence $failed $operation: $reason")

        where:
        operation                                   | url                                                        | response                                                  | call                                                                   || failed       | reason
        'find the page Notes in space DSO'          | CONTENT + '?type=page&spaceKey=DSO&title=Notes&expand=space,version&limit=50' | withServerError()                        | ({ ConfluenceAdapter it -> it.find('DSO', 'Notes') })                  || 'could not'  | '500 Internal Server Error'
        'read the page 100001'                      | CONTENT + '/100001?expand=space,version,body.storage'      | withSuccess('{}', APPLICATION_JSON)                       | ({ ConfluenceAdapter it -> it.get('100001') })                         || 'could not'  | NO_CONTENT
        'create the page Change CHG0031001 in space DSO' | CONTENT + '?expand=space,version,body.storage'        | withBadRequest().body('{"message":"A page with this title already exists"}') | ({ ConfluenceAdapter it -> it.create(newPage()) }) || 'refused to' | '400 Bad Request'
        'update the page 100001'                    | CONTENT + '/100001?expand=space,version,body.storage'      | withServerError()                                         | ({ ConfluenceAdapter it -> it.update('100001', pageUpdate()) })       || 'could not'  | '500 Internal Server Error'
        type = failed == 'refused to' ? RefusedException : UncheckedIOException
    }

    private static RequestMatcher body(Map expected) {
        { ClientHttpRequest request ->
            assert new JsonSlurper().parseText((request as MockClientHttpRequest).bodyAsString) == expected
        } as RequestMatcher
    }
}
