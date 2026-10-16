package com.bbh.itss.beadle.internal.application.confluence

import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException
import spock.lang.Specification

import static com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage.REQUIRED
import static com.bbh.itss.beadle.internal.support.Fixtures.newPage
import static com.bbh.itss.beadle.internal.support.Fixtures.page
import static com.bbh.itss.beadle.internal.support.Fixtures.pageUpdate

class ConfluenceServiceSpec extends Specification {

    ConfluencePort confluence = Mock()
    def service = new ConfluenceService(confluence)

    def "pages are found by the trimmed space and title, without their bodies"() {
        when:
        def found = service.find(' DSO ', ' Beadle release notes ')

        then:
        1 * confluence.find('DSO', 'Beadle release notes') >> [page(), page(id: '100002', body: null)]
        found == [page(body: null), page(id: '100002', body: null)]
    }

    def "a search without #missing is refused before Confluence is asked"() {
        when:
        service.find(spaceKey, title)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == fields
        refused.problems*.message == fields.collect { REQUIRED }
        0 * confluence._

        where:
        spaceKey | title     || missing              | fields
        ' '      | 'Notes'   || 'a space'            | ['spaceKey']
        'DSO'    | null      || 'a title'            | ['title']
        null     | ''        || 'a space and a title' | ['spaceKey', 'title']
    }

    def "a page is read by its trimmed id with its body"() {
        when:
        def found = service.get(' 100001 ')

        then:
        1 * confluence.get('100001') >> page()
        found == page()
    }

    def "a page without an id is not found without asking Confluence"() {
        when:
        service.get(id)

        then:
        thrown(NoSuchElementException)
        0 * confluence._

        where:
        id << [null, ' ']
    }

    def "a page is created and updated through Confluence"() {
        given:
        def created = page(id: '100009', title: 'Change CHG0031001', version: 1)
        def updated = page(version: 4, body: '<p>Beadle 1.1</p>')

        when:
        def creation = service.create(newPage())
        def update = service.update(' 100001 ', pageUpdate())

        then:
        1 * confluence.create(newPage()) >> created
        1 * confluence.update('100001', pageUpdate()) >> updated
        creation == created
        update == updated
    }
}
