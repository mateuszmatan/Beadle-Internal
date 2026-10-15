package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

class ConfluenceRegressionSpec extends BeadleInternalSpecification {

    def "a page is found by its space and title without its body, and read with it"() {
        when:
        def found = api.get('/api/internal/confluence/pages?spaceKey=dso&title=' + enc('beadle release notes'))

        then:
        found.status == 200
        found.json.size() == 1
        found.json[0].keySet() == ['id', 'spaceKey', 'title', 'version', 'url'] as Set
        found.json[0].subMap('spaceKey', 'title', 'version') == [spaceKey: 'DSO', title: 'Beadle release notes', version: 3]

        when:
        def read = api.get("/api/internal/confluence/pages/${found.json[0].id}")

        then:
        read.status == 200
        read.json == found.json[0] + [body: '<h2>Beadle 1.0</h2><p>First release of the change tooling.</p>']
        api.get('/api/internal/confluence/pages?spaceKey=DSO&title=' + enc('No such page')).json == []
    }

    def "a page is created under a parent and updated at the version it replaces, one version up"() {
        given:
        def title = uniqueName('Change CHG0031001')

        when:
        def created = api.post('/api/internal/confluence/pages',
                [spaceKey: 'dso', title: title, parentId: '100001', body: '<p>Evidence</p>'])

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/internal/confluence/pages/$created.json.id".toString()
        created.json.subMap('spaceKey', 'title', 'version', 'body') == [spaceKey: 'DSO', title: title, version: 1, body: '<p>Evidence</p>']

        when:
        def updated = api.put("/api/internal/confluence/pages/$created.json.id",
                [title: "$title (approved)".toString(), body: '<p>Evidence, approved</p>', version: 1])

        then:
        updated.status == 200
        updated.json == created.json + [title: "$title (approved)".toString(), body: '<p>Evidence, approved</p>', version: 2]
        api.get("/api/internal/confluence/pages/$created.json.id").json == updated.json

        when:
        def stale = api.put("/api/internal/confluence/pages/$created.json.id", [title: title, body: '<p>Late</p>', version: 1])

        then:
        stale.status == 409
        stale.json.detail.contains('changed by someone else')
        api.get("/api/internal/confluence/pages/$created.json.id").json.version == 2

        and:
        callLog()*.subMap('system', 'operation', 'outcome') == [
                [system: 'CONFLUENCE', operation: 'create', outcome: 'SUCCESS'],
                [system: 'CONFLUENCE', operation: 'update', outcome: 'SUCCESS'],
                [system: 'CONFLUENCE', operation: 'get', outcome: 'SUCCESS'],
                [system: 'CONFLUENCE', operation: 'update', outcome: 'FAILURE'],
                [system: 'CONFLUENCE', operation: 'get', outcome: 'SUCCESS']]
        callLog()[0].target == "dso/$title".toString()
    }

    def "a second page with the same title in the space is refused"() {
        when:
        def response = api.post('/api/internal/confluence/pages',
                [spaceKey: 'DSO', title: 'Beadle release notes', parentId: null, body: '<p>Again</p>'])

        then:
        response.status == 409
        response.json.detail == 'Confluence already has a page titled Beadle release notes in space DSO'
    }

    def "a page request with #problem is refused naming the fields"() {
        when:
        def response = method == 'POST' ? api.post(path, body) : method == 'PUT' ? api.put(path, body) : api.get(path)

        then:
        response.status == 400
        response.json.problems*.field == fields
        callLog() == []

        where:
        problem                   | method | path                                            | body                                || fields
        'nothing in a new page'   | 'POST' | '/api/internal/confluence/pages'                | [:]                                 || ['spaceKey', 'title', 'body']
        'no version in an update' | 'PUT'  | '/api/internal/confluence/pages/100001'         | [title: 'Notes', body: '<p>x</p>']  || ['version']
        'no title to search'      | 'GET'  | '/api/internal/confluence/pages?spaceKey=DSO'   | null                                || ['title']
    }

    def "a page Confluence does not have is not found"() {
        expect:
        api.get('/api/internal/confluence/pages/999').status == 404
        api.put('/api/internal/confluence/pages/999', [title: 'Notes', body: '<p>x</p>', version: 1]).status == 404
    }

    private static String enc(String text) {
        encode(text, UTF_8).replace('+', '%20')
    }
}
