package com.bbh.itss.beadle.internal.regression

import com.bbh.itss.beadle.internal.support.BeadleInternalSpecification

/**
 * The release notes and change evidence Beadle-GUI keeps in Confluence.
 */
class ConfluenceRegressionSpec extends BeadleInternalSpecification {

    def "a page is found by its space and title without its body, and read with it"() {
        when:
        def found = api.get('/api/internal/confluence/pages?spaceKey=dso&title=beadle%20release%20notes')
        def read = api.get("/api/internal/confluence/pages/${found.json[0].id}")

        then:
        found.json*.subMap('spaceKey', 'title', 'version') == [[spaceKey: 'DSO', title: 'Beadle release notes', version: 3]]
        found.json[0].keySet() == ['id', 'spaceKey', 'title', 'version', 'url'] as Set
        read.json == found.json[0] + [body: '<h2>Beadle 1.0</h2><p>First release of the change tooling.</p>']
    }

    def "a page is created, updated one version up, and an update of the version it replaced is refused"() {
        given:
        def title = 'Change ' + UUID.randomUUID().toString().take(8)

        when:
        def created = api.post('/api/internal/confluence/pages',
                [spaceKey: 'DSO', title: title, parentId: '100001', body: '<p>Evidence</p>'])
        def updated = api.put("/api/internal/confluence/pages/$created.json.id",
                [title: title + ' (approved)', body: '<p>Approved</p>', version: 1])
        def stale = api.put("/api/internal/confluence/pages/$created.json.id", [title: title, body: '<p>Late</p>', version: 1])

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/internal/confluence/pages/$created.json.id".toString()
        created.json.subMap('spaceKey', 'title', 'version', 'body') == [spaceKey: 'DSO', title: title, version: 1, body: '<p>Evidence</p>']
        updated.status == 200
        updated.json == created.json + [title: title + ' (approved)', body: '<p>Approved</p>', version: 2]
        stale.status == 409
        stale.json.detail == 'The record was changed by someone else in the meantime. Reload it and apply your change again.'
        api.get("/api/internal/confluence/pages/$created.json.id").json == updated.json

        and:
        callLog()*.subMap('operation', 'target', 'outcome') == [
                [operation: 'create', target: "DSO/$title".toString(), outcome: 'SUCCESS'],
                [operation: 'update', target: "$created.json.id $title (approved)".toString(), outcome: 'SUCCESS'],
                [operation: 'update', target: "$created.json.id $title".toString(), outcome: 'FAILURE'],
                [operation: 'get', target: created.json.id, outcome: 'SUCCESS']]
    }
}
